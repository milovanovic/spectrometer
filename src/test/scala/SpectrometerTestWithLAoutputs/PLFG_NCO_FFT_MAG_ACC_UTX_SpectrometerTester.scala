package spectrometerLA

import freechips.rocketchip.interrupts._
import dsptools._
import dsptools.numbers._
import chisel3._
import chisel3.util._
import chisel3.experimental._
import chisel3.iotesters.{Driver, PeekPokeTester}

import dspblocks.{AXI4DspBlock, AXI4StandaloneBlock, TLDspBlock, TLStandaloneBlock}
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.config.Parameters
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.system.BaseConfig

import org.scalatest.{FlatSpec, Matchers}
import breeze.math.Complex
import breeze.signal.{fourierTr, iFourierTr}
import breeze.linalg._
import breeze.plot._

import plfg._
import nco._
import fft._
import uart._
import splitter._
import magnitude._
import accumulator._

import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PLFG -> NCO -> FFT -> MAG -> ACC -> uTx
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PLFG_NCO_FFT_MAG_ACC_UTX_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get

  // This signals should be always ready!
  poke(dut.laInside.ready, true.B)
  poke(dut.laOutside.ready, true.B)

  // Splitters
  // memWriteWord(params.inSplitAddress.base + 0x0, 0)   // set ready to AND
  // memWriteWord(params.plfgSplitAddress.base + 0x0, 0) // set ready to AND
  // memWriteWord(params.ncoSplitAddress.base + 0x0, 0)  // set ready to AND
  // memWriteWord(params.fftSplitAddress.base + 0x0, 0)  // set ready to AND
  // memWriteWord(params.magSplitAddress.base + 0x0, 0)  // set ready to AND
  // memWriteWord(params.uRxSplitAddress.base + 0x0, 0)  // set ready to AND
  // memWriteWord(params.outSplitAddress.base + 0x0, 0)  // set ready to AND

  val numAccuWin = 4 // Number of accumulator windows
  
  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
    
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, numAccuWin*2) // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  //memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 1)          // start value
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 4)            // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  
  // Mux
  memWriteWord(params.plfgMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.plfgMuxAddress1.base + 0x4, 0x0) // output1

  memWriteWord(params.ncoMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.ncoMuxAddress1.base + 0x4, 0x0) // output1

  memWriteWord(params.fftMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.fftMuxAddress1.base + 0x4, 0x0) // output1

    memWriteWord(params.magMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.magMuxAddress1.base + 0x4, 0x0) // output1

  memWriteWord(params.plfgMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x0) // output1

  memWriteWord(params.ncoMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x0) // output1

  memWriteWord(params.fftMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.fftMuxAddress0.base + 0x4, 0x0) // output1

  memWriteWord(params.magMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.magMuxAddress0.base + 0x4, 0x0) // output1

    // memWriteWord(params.outMuxAddress.base,       0x0) // output0
  memWriteWord(params.outMuxAddress.base + 0x4, 0x0) // output1
  // memWriteWord(params.outMuxAddress.base + 0x8, 0x3) // output2

  // magAddress
  memWriteWord(params.magAddress.base, 0x2) // set jpl magnitude
  
  memWriteWord(params.accAddress.base, params.fftParams.numPoints)  // set number of fft points
  memWriteWord(params.accAddress.base + 0x4, numAccuWin)            // set number of accumulated fft windows

  // UART
  memWriteWord(params.uartParams.address + 0x08, 1) // enable Tx

  var outSeq = Seq[Int]()
  var uartSeq = Seq[Int]()
  var peekedVal: Int = 0

  var dataCnt = 0
  var readUart = 0
  var oldRead = 1
  var newRead = 1

  // check only one fft window 
  while (uartSeq.length < params.fftParams.numPoints * 16) {
    newRead = 1
    oldRead = 1
    while (readUart == 0){
      oldRead = peek(dut.module.uTx).toInt
      step(1)
      newRead = peek(dut.module.uTx).toInt
      if (newRead == 0 && oldRead == 1){
        readUart = 1
        // poke(dut.outStream.ready, true.B)
      }
    }
      readUart = 0  // set flag back to 0
      step(params.divisorInit + 1) // skip start bit
      dataCnt = 0
      while (dataCnt < 9) {
        if (dataCnt < 8) {
          peekedVal = peek(dut.module.uTx).toInt
          uartSeq = uartSeq :+ peekedVal
        }
        dataCnt = dataCnt + 1
        step(params.divisorInit + 1)
      }
      
      assert(peek(dut.module.uTx).toInt == 1, "Expected stop bit, got 0")
  }

  var realSeq = Seq[Int]()
  var tmpReal: Int = 0
  
  for (i <- 0 until uartSeq.length by 16) {
    tmpReal = java.lang.Integer.parseInt(SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 15), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 14), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 13), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 12), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 11), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 10), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 9), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 8), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 7), 1) ++ 
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 6), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 5), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 4), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 3), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 2), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 1), 1) ++
                                         SpectrometerTesterUtils.asNdigitBinary(uartSeq(i + 0), 1), 2).toInt
    realSeq = realSeq :+ tmpReal
  }

  // Output data
  val chiselFFTForPlot = realSeq.map(c => c.toLong).toSeq

  // Plot accelerator data
  if (enablePlot == true)
    SpectrometerTesterUtils.plot_fft(inputData = chiselFFTForPlot, plotName = "PLFG -> NCO -> FFT -> MAG -> ACC -> UTX", fileName = "SpectrometerTestWithLAoutputs/plfg_nco_fft_mag_acc_utx/plot.pdf")

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTestWithLAoutputs/plfg_nco_fft_mag_acc_utx/GoldenData.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + "\n")
  }
  w.close
}
