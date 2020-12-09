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

import plfg._
import nco._
import fft._
import uart._
import splitter._
import magnitude._
import accumulator._

import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PLFG -> NCO -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PLFG_NCO_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
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

  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
  
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, 4)            // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  //memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 1)          // start value
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 16)           // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  
  memWriteWord(params.plfgMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.plfgMuxAddress1.base + 0x4, 0x0) // output1
  
  // memWriteWord(params.ncoMuxAddress1.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress1.base + 0x4, 0x0) // output1
  // test multiplier inside nco module - MP
  // memWriteWord(params.ncoAddress.base, 0x1)
  // memWriteWord(params.ncoAddress.base + 0x4, 0x00002000)
  // memWriteWord(params.fftMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.fftMuxAddress1.base + 0x4, 0x0) // output1
  // memWriteWord(params.magMuxAddress1.base,       0x0) // output0
  // memWriteWord(params.magMuxAddress1.base + 0x4, 0x0) // output1

  memWriteWord(params.plfgMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x0) // output1
  // memWriteWord(params.ncoMuxAddress0.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x0) // output1
  // memWriteWord(params.fftMuxAddress0.base,       0x0) // output0
  // memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1) // output1
  
  memWriteWord(params.outMuxAddress.base, 0x3) // output0
  // memWriteWord(params.outMuxAddress.base + 0x4, 0x0) // output1
  // memWriteWord(params.outMuxAddress.base + 0x8, 0x3) // output2
  
  poke(dut.outStream.ready, true.B)

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  // check 1024 data samples
  while (outSeq.length < params.fftParams.numPoints * 4) {
    if (peek(dut.outStream.valid) == 1 && peek(dut.outStream.ready) == 1) {
      peekedVal = peek(dut.outStream.bits.data)
      outSeq = outSeq :+ peekedVal.toInt
    }
    step(1)
  }
  var realSeq = Seq[Int]()
  var imagSeq = Seq[Int]()
  var tmpReal: Short = 0
  var tmpImag: Short = 0
  
  for (i <- 0 until outSeq.length by 4) {
    tmpReal = java.lang.Integer.parseInt(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 3), 8) ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 2), 8), 2).toShort
    tmpImag = java.lang.Long.parseLong(SpectrometerTesterUtils.asNdigitBinary(outSeq(i + 1), 8)   ++ SpectrometerTesterUtils.asNdigitBinary(outSeq(i), 8), 2).toShort
    realSeq = realSeq :+ tmpReal.toInt
    imagSeq = imagSeq :+ tmpImag.toInt
  }
  // Plot accelerator data
  SpectrometerTesterUtils.plot_data(inputData = realSeq.map(c => c.toInt), plotName = "PLFG -> NCO -> POUT (COS)", fileName = "SpectrometerTest/plfg_nco_pout/plot_cos.pdf")
  SpectrometerTesterUtils.plot_data(inputData = imagSeq.map(c => c.toInt), plotName = "PLFG -> NCO -> POUT (SIN)", fileName = "SpectrometerTest/plfg_nco_pout/plot_sin.pdf")

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTest/plfg_nco_pout/data_cos.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + "\n")
  }
  w.close

  // Write output data to text file
  val file1 = new File("./test_run_dir/SpectrometerTest/plfg_nco_pout/data_sin.txt")
  val w1 = new BufferedWriter(new FileWriter(file1))
  for (i <- 0 until imagSeq.length ) {
    w1.write(f"${imagSeq(i)}%04x" + "\n")
  }
  w1.close
}

