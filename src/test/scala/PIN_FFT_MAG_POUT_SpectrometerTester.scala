// SPDX-License-Identifier: Apache-2.0

package spectrometer


import chisel3.iotesters.PeekPokeTester

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._

import breeze.math.Complex
import breeze.signal.{fourierTr}
import breeze.linalg._

import java.io._


//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> FFT -> MAG -> POUT
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_FFT_MAG_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {

  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)
  
  val binWithPeak = 2
  val fftSize = params.fftParams.numPoints
  val inData = SpectrometerTesterUtils.getTone(numSamples = fftSize, binWithPeak.toDouble/fftSize.toDouble)
  
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

  memWriteWord(params.magSplitAddress.base + 0x0, 1)   // set ready to OR
  memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x1) // in split must have ready active
  memWriteWord(params.fftMuxAddress1.base, 0x0)        // output0
  memWriteWord(params.magMuxAddress1.base + 0x4, 0x0)  // output1
  memWriteWord(params.ncoMuxAddress0.base, 0x1)        // output0
  
  memWriteWord(params.fftMuxAddress0.base, 0x0)        // output0
  memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1)  // output1
  memWriteWord(params.magMuxAddress0.base + 0x4, 0x1)  // output1
  memWriteWord(params.outMuxAddress.base, 0x1)         // output0
  memWriteWord(params.outMuxAddress.base + 0x8, 0x5)   // output2
  // magAddress
  memWriteWord(params.magAddress.base, 0x2)            // set jpl magnitude
  poke(dut.outStream.ready, true)

  step(1)
   // add master transactions
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))

  var outSeq = Seq[Int]()
  var peekedVal: BigInt = 0
  
  // check only one fft window 
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
  
  // Scala fft
  val fftScala = fourierTr(DenseVector(inData.toArray)).toScalaVector.map(c => Complex(c.real/fftSize, c.imag/fftSize))
  val fftMagScala = fftScala.map(c => c.abs.toInt)
  
  imagSeq.foreach( c => println(c.toString))
  fftMagScala.foreach( c => println(c.toString))
  
  // check tolerance
  if (params.fftParams.useBitReverse) {
    SpectrometerTesterUtils.checkDataError(imagSeq, fftMagScala, 3)
  }
  else {
    val bRImag = SpectrometerTesterUtils.bitrevorder_data(imagSeq)
    SpectrometerTesterUtils.checkDataError(bRImag, fftMagScala, 3)
  }
  
  if (enablePlot) {
    SpectrometerTesterUtils.plot_data(inputData = inData, plotName = "Input data", fileName = "SpectrometerTest/pin_fft_mag_pout/plot_in.pdf")
    SpectrometerTesterUtils.plot_data(inputData = imagSeq.map(c => c.toInt), plotName = "PIN -> FFT -> MAG -> POUT", fileName = "SpectrometerTest/pin_fft_mag_pout/plot_mag.pdf")
  }

  stepToCompletion(silentFail = silentFail)
}
