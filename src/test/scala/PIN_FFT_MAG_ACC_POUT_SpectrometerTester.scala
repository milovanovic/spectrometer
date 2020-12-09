// SPDX-License-Identifier: Apache-2.0

package spectrometer

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
// PIN -> FFT -> MAG -> ACC -> POUT
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_FFT_MAG_ACC_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  //dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)
  
  val inData = SpectrometerTesterUtils.getTone(numSamples = params.fftParams.numPoints, 0.03125)

  // split 32 bit data to 4 bytes and send real sinusoid
  var dataByte = Seq[Int]()
  for (i <- inData) {
    // imag part
    dataByte = dataByte :+ 0
    dataByte = dataByte :+ 0
    // real part
    dataByte = dataByte :+ ((i)        & 0xFF)
    dataByte = dataByte :+ ((i >>> 8)  & 0xFF)
  }
  
    // This signals should be always ready!
  poke(dut.laInside.ready, true.B)
  poke(dut.laOutside.ready, true.B)
  
  val numAccuWin = 4 // Numver of accumulator windows

  // Splitters
  // memWriteWord(params.inSplitAddress.base  + 0x0, 0) // set ready to AND
  // memWriteWord(params.ncoSplitAddress.base + 0x0, 0) // set ready to AND
  // memWriteWord(params.fftSplitAddress.base + 0x0, 1) // set ready to OR
  // memWriteWord(params.magSplitAddress.base + 0x0, 1) // set ready to OR
  // memWriteWord(params.uRxSplitAddress.base + 0x0, 0) // set ready to AND

  // Mux
  // memWriteWord(params.ncoMuxAddress1.base,       0x0) // output0   
  // memWriteWord(params.ncoMuxAddress1.base + 0x4, 0x1) // output1   

  memWriteWord(params.fftMuxAddress1.base,       0x0) // output0   
  // memWriteWord(params.fftMuxAddress1.base + 0x4, 0x0) // output1   
  // added - MP
  memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x1) //in split must have ready active

  memWriteWord(params.magMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.magMuxAddress1.base + 0x4, 0x0) // output1  

  memWriteWord(params.ncoMuxAddress0.base,       0x1) // output0   
  // memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x2) // output1   

  memWriteWord(params.fftMuxAddress0.base,       0x0) // output0
  memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1) // output1  

  memWriteWord(params.magMuxAddress0.base,       0x0) // output0
  memWriteWord(params.magMuxAddress0.base + 0x4, 0x1) // output1  

  memWriteWord(params.outMuxAddress.base,       0x0) // output0
  // memWriteWord(params.outMuxAddress.base + 0x4, 0x2) // output1
 // memWriteWord(params.outMuxAddress.base + 0x8, 0x4) // output2
  memWriteWord(params.outMuxAddress.base + 0x8, 0x5) // output2 MP - added!

  
  memWriteWord(params.magAddress.base, 0x2) // set jpl magnitude
  memWriteWord(params.accAddress.base, params.fftParams.numPoints)  // set number of fft points
  memWriteWord(params.accAddress.base + 0x4, numAccuWin)            // set number of accumulated fft windows

  // memWriteWord(params.uartParams.address + 0x08, 1) // enable Tx
  // memWriteWord(params.uartParams.address + 0x0c, 1) // enable Rx
  poke(dut.outStream.ready, true.B)

  step(1)
   // add master transactions
  for (j <- 0 until numAccuWin*2+1) {
    master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  }

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0

 // check only one fft window 
  while (outSeq.length < params.fftParams.numPoints * 2) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedVal = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }

  var realSeq = Seq[Int]()
  var tmpReal: Short = 0
  
  for (i <- 0 until outSeq.length by 2) {
    tmpReal = java.lang.Integer.parseInt(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 1), 8) ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 0), 8), 2).toShort
    realSeq = realSeq :+ tmpReal.toInt
  }
  
  // Output data
  val chiselFFTForPlot = if (params.accParams.bitReversal || params.fftParams.useBitReverse) realSeq.map(c => c.toLong).toSeq else SpectrometerTesterUtils.bitrevorder_data(realSeq).map(c => c.toLong)

  // Plot accelerator data
  SpectrometerTesterUtils.plot_fft(inputData = chiselFFTForPlot, plotName = "PIN -> FFT -> MAG -> ACC -> POUT", fileName = "SpectrometerTest/pin_fft_mag_acc_pout.pdf")

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTest/data.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + "\n")
  }
  w.close

  stepToCompletion(silentFail = silentFail)
}
