// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3._
import chisel3.util._
import chisel3.iotesters.{Driver, PeekPokeTester}

import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.amba.axi4._

import org.scalatest.{FlatSpec, Matchers}


import java.io._

//---------------------------------------------------------------------------------------------------------------------------------------------------------------
// PIN -> POUT
//---------------------------------------------------------------------------------------------------------------------------------------------------------------
class PIN_POUT_SpectrometerTester 
(
  dut: SpectrometerTest with SpectrometerTestPins,
  params: SpectrometerTestParameters,
  enablePlot: Boolean = true,
  silentFail: Boolean = false
) extends PeekPokeTester(dut.module) with AXI4StreamModel with AXI4MasterModel {
  
  val mod = dut.module
  def memAXI: AXI4Bundle = dut.ioMem.get
  val master = bindMaster(dut.inStream)
  
  // Send real data
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
  
  memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x1) // output1
  memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x1)  // output1
  memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1)  // output1
  memWriteWord(params.magMuxAddress0.base + 0x4, 0x1)  // output1
  memWriteWord(params.outMuxAddress.base, 0x5)         // output0
  
  poke(dut.outStream.ready, true)

  step(1)
   // add master transactions
  master.addTransactions((0 until dataByte.size).map(i => AXI4StreamTransaction(data = dataByte(i))))

  var outSeq = Seq[Int]()
  var temp: Int = 0
  var i = 0

  while (outSeq.length < params.fftParams.numPoints) {
    if (peek(dut.outStream.valid)==1) {
      outSeq = outSeq :+ peek(dut.outStream.bits.data).toInt
    }
    step(1)
  }
  
  SpectrometerTesterUtils.checkDataError(dataByte, outSeq, 1)
  stepToCompletion(silentFail = silentFail)
}
