// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3.iotesters.PeekPokeTester

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._

import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PLFG -> NCO -> parallel_out
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PLFG_NCO_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val fftSize = params.fftParams.numPoints
  
  val binWithPeak = 2
  val startValue = (binWithPeak * 4 * params.ncoParams.tableSize)/fftSize
  val complexNcoOut = SpectrometerTesterUtils.calcExpectedNcoOut(fftSize, binWithPeak)
  val expectedSin = complexNcoOut.map(c => c.imag.toInt)
  val expectedCos = complexNcoOut.map(c => c.real.toInt)
  
  // plfg setup
  val segmentNumsArrayOffset = 6 * params.beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes
  
  memWriteWord(params.plfgRAM.base, 0x24000000)
  memWriteWord(params.plfgAddress.base + 2*params.beatBytes, 4)            // number of frames
  memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
  memWriteWord(params.plfgAddress.base + 5*params.beatBytes, startValue)   // start value
  memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
  memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
  memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
  memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
  memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1
  
  memWriteWord(params.plfgMuxAddress1.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress1.base + 0x4, 0x0)  // output1
  
  /// test multiplier inside nco module
  // memWriteWord(params.ncoAddress.base, 0x1)
  // memWriteWord(params.ncoAddress.base + 0x4, 0x00002000)

  memWriteWord(params.plfgMuxAddress0.base,       0x0) // output0
  memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x0)  // output1
  memWriteWord(params.outMuxAddress.base, 0x3)         // output0

  poke(dut.outStream.ready, true)

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  while (outSeq.length < fftSize * 4) {
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
  
  if (enablePlot) {
    SpectrometerTesterUtils.plot_data(inputData = realSeq.map(c => c.toInt), plotName = "PLFG -> NCO -> POUT (COS)", fileName = "SpectrometerTest/plfg_nco_pout/plot_cos.pdf")
    SpectrometerTesterUtils.plot_data(inputData = imagSeq.map(c => c.toInt), plotName = "PLFG -> NCO -> POUT (SIN)", fileName = "SpectrometerTest/plfg_nco_pout/plot_sin.pdf")
  }
  
  println("Expected cos is:")
  expectedCos.map(c => println(c.toString))
  println("Received cos is:")
  realSeq.map(c => println(c.toString))
  
  SpectrometerTesterUtils.checkDataError(expectedCos, realSeq)
  SpectrometerTesterUtils.checkDataError(expectedSin, imagSeq)
  
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

