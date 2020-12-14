// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3.iotesters.PeekPokeTester

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.diplomacy._

import breeze.math.Complex
import breeze.signal.fourierTr
import breeze.linalg._

import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> FFT -> MAG -> ACC -> POUT
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_FFT_MAG_ACC_POUT_SpectrometerTester
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = false,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)
  
  val fftSize = params.fftParams.numPoints
  val binWithPeak = 2
  // generate only real sinusoid
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

  // Write inpput data to text file
  val filein = new File("./test_run_dir/SpectrometerTest/pin_fft_mag_acc_pout/input.txt")
  val win = new BufferedWriter(new FileWriter(filein))
  for (i <- 0 until dataByte.length ) {
    win.write(f"${dataByte(i)}%02x" + "\n")
  }
  win.close

  val numAccuWin = 4 // Number of accumulated windows

  memWriteWord(params.fftMuxAddress1.base, 0x0)        // output0
  memWriteWord(params.magMuxAddress1.base, 0x0)        // output0
  memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x1) // output1
  memWriteWord(params.ncoMuxAddress0.base, 0x1)        // output0

  memWriteWord(params.fftMuxAddress0.base, 0x0)        // output0
  memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1)  // output1

  memWriteWord(params.magMuxAddress0.base, 0x0)        // output0
  memWriteWord(params.magMuxAddress0.base + 0x4, 0x1)  // output1

  memWriteWord(params.outMuxAddress.base, 0x0)         // output0
  memWriteWord(params.outMuxAddress.base + 0x8, 0x5)   // output2
  
  // magAddress
  memWriteWord(params.magAddress.base, 0x2) // set jpl magnitude
  
  memWriteWord(params.accAddress.base, params.fftParams.numPoints)  // set number of fft points
  memWriteWord(params.accAddress.base + 0x4, numAccuWin)            // set number of accumulated fft windows
  
  poke(dut.outStream.ready, true)

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
  
  // Scala fft
  val fftScala = fourierTr(DenseVector(inData.toArray)).toScalaVector.map(c => Complex(c.real/fftSize, c.imag/fftSize))
  val fftMagScala = fftScala.map(c => c.abs.toInt)
  
  // check tolerance
  if (params.fftParams.useBitReverse || params.accParams.bitReversal) {
    SpectrometerTesterUtils.checkDataError(realSeq, fftMagScala, 2)
  }
  else {
    val bRReal = SpectrometerTesterUtils.bitrevorder_data(realSeq)
    SpectrometerTesterUtils.checkDataError(realSeq, fftMagScala, 2)
  }
  
  val chiselFFTForPlot = realSeq.map(c => c.toLong).toSeq
  // Plot accelerator data
  if (enablePlot == true)
    SpectrometerTesterUtils.plot_fft(inputData = chiselFFTForPlot, plotName = "PIN -> FFT -> MAG -> ACC -> POUT", fileName = "SpectrometerTest/pin_fft_mag_acc_pout/plot.pdf")

  // Write output data to text file
  val file = new File("./test_run_dir/SpectrometerTest/pin_fft_mag_acc_pout/GoldenData.txt")
  val w = new BufferedWriter(new FileWriter(file))
  for (i <- 0 until realSeq.length ) {
    w.write(f"${realSeq(i)}%04x" + "\n")
  }
  w.close
}
