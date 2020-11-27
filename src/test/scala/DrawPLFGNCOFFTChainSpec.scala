package spectrometer

import chisel3._
import chisel3.util._
import chisel3.experimental._
import dsptools._
import dsptools.numbers._
import breeze.math.Complex
import breeze.signal.{fourierTr, iFourierTr}
import breeze.linalg.{DenseVector, randomDouble}
import chisel3.util.log2Up
import scala.math.{Pi, pow, sqrt}
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import scala.io.Source
import breeze.plot._
import java.io._

import dspblocks._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import chisel3.iotesters.Driver
import chisel3.iotesters.PeekPokeTester
import org.scalatest.{FlatSpec, Matchers}
import fft._
import plfg._
import nco._

class DrawPLFGNCOFFTChainTester(   
  dut: PLFGNCOFFTChain[FixedPoint],
  csrAddressPLFG: AddressSet,
  ramAddress: AddressSet,
  csrAddressNCO: AddressSet,
  csrAddressFFT: AddressSet,
  beatBytes: Int
)  extends PeekPokeTester(dut.module) with AXI4MasterModel {

  
  override def memAXI: AXI4Bundle = dut.ioMem.get.getWrappedValue
  val segmentNumsArrayOffset = 6 * beatBytes
  val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * beatBytes
  val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * beatBytes
  
  val returnVal = new Array[BigInt](1024)
  val returnVal1 = new Array[Int](1024)
  val real = new Array[Double](1024)
  val imag = new Array[Double](1024)
  val absVals = new Array[Double](1024)
  var idx = 0

  //memWriteWord(ramAddress.base, 0x24000000)
  //memWriteWord(ramAddress.base, 0x18000001)
  memWriteWord(ramAddress.base, 0x60000001)
  step(1)
  memWriteWord(ramAddress.base + beatBytes, 0x60000001)
  step(1)
  memWriteWord(ramAddress.base + 2*beatBytes, 0x60000001)
  step(1)
  memWriteWord(ramAddress.base + 3*beatBytes, 0x61000001)
  step(1)
  memWriteWord(ramAddress.base + 4*beatBytes, 0x20000001)
  step(1)
  memWriteWord(ramAddress.base  + 5*beatBytes, 0x20000001)
  step(1)
  memWriteWord(ramAddress.base + 6*beatBytes, 0x20000001)
  step(1)
  memWriteWord(ramAddress.base + 7*beatBytes, 0x20000001)
  step(1)
  //memWriteWord(ramAddress.base + beatBytes, 0x200A0003)
  step(1)
  memWriteWord(csrAddressPLFG.base + 2*beatBytes, 1) // frameNum
  step(1)
  //memWriteWord(csrAddressPLFG.base + 3*beatBytes, 60) // interframeNumOfSamples
  //step(1)
  memWriteWord(csrAddressPLFG.base + 4*beatBytes, 1) // differentChirpsNum
  step(1)
  memWriteWord(csrAddressPLFG.base + 5*beatBytes, 1) // startingPoint
  step(1)
  memWriteWord(csrAddressPLFG.base + segmentNumsArrayOffset, 8) // segmentNums
  step(1)
  memWriteWord(csrAddressPLFG.base + repeatedChirpNumsArrayOffset, 1)
  step(1)
  memWriteWord(csrAddressPLFG.base + chirpOrdinalNumsArrayOffset, 0)
  step(1)
  
  memWriteWord(csrAddressFFT.base, 1024)
  step(1)
  memWriteWord(csrAddressFFT.base + beatBytes, 1)
  step(1)
  
  
  memWriteWord(csrAddressPLFG.base + beatBytes, 0) // reset_bit
  step(1)
  memWriteWord(csrAddressPLFG.base, 1) // enable
  step(1)
  poke(dut.outStream.ready, 1)
  step(1)
  //step(4500)
  
  var ii = 0
/*  
  while ((ii < 4500) && (idx < 1024)) {
    if((peek(dut.outStream.ready) > 0) && (peek(dut.outStream.valid) > 0)){
      returnVal(idx) = peek(dut.outStream.bits.data)
      returnVal1(idx) = returnVal(idx).toInt
      real(idx) = ((returnVal1(idx) / pow(2,16)).toShort).toDouble
      imag(idx) = ((returnVal1(idx) - (real(idx).toInt * pow(2,16))).toShort).toDouble
      absVals(idx) = sqrt(pow(real(idx), 2) + pow(imag(idx), 2)).toDouble
      idx += 1
    }
    step(1)
    ii +=1
  }*/
  
  while (idx < 4*8*1024) {
    if((peek(dut.outStream.ready) > 0) && (peek(dut.outStream.valid) > 0)){
      returnVal(idx%1024) = peek(dut.outStream.bits.data)
      returnVal1(idx%1024) = returnVal(idx%1024).toInt
      real(idx%1024) = ((returnVal1(idx%1024) / pow(2,16)).toShort).toDouble
      imag(idx%1024) = ((returnVal1(idx%1024) - (real(idx%1024).toInt * pow(2,16))).toShort).toDouble
      absVals(idx%1024) = sqrt(pow(real(idx%1024), 2) + pow(imag(idx%1024), 2)).toDouble
      idx += 1
    }
    step(1)
    ii +=1
  }
  
  val f1 = Figure("PLFG NCO FFT Chain Output")
  val p1 = f1.subplot(1,1,0)
  p1.legend_= (true)
  val xaxis1 = (0 until absVals.length).map(e => e.toDouble).toSeq.toArray
  p1.setXAxisIntegerTickUnits()
  p1 += plot(xaxis1, absVals.toArray, name = "FFT Absolute value")
  p1.ylim(absVals.min, absVals.max)
  //p1.ylim(returnVal1.min, returnVal1.max)
  p1.xlabel = "Frequency"
  p1.ylabel = "FFT values"
  f1.saveas(s"test_run_dir/plfg_nco_fft_chain.pdf")
  
  /*val returnVal1 = returnVal.map(c => c.asSInt).toSeq
  val real = returnVal1.map(c => c(31, 16)).toSeq
  val imag = returnVal1.map(c => c(15, 0)).toSeq
  
  val pw1 = new PrintWriter(new File("imagsTwoSided.txt" ))
  imag.foreach(x => pw1.println(x.toString(2)))
  pw1.close
  
  val pw2 = new PrintWriter(new File("realsTwoSided.txt" ))
  real.foreach(x => pw2.println(x.toString(2)))
  pw2.close*/

}


class DrawPLFGNCOFFTChainSpec extends FlatSpec with Matchers {
  implicit val p: Parameters = Parameters.empty

  val beatBytes = 4

  val paramsNCO = FixedNCOParams(
    tableSize = 64,
    tableWidth = 16,
    phaseWidth = 18,
    rasterizedMode = false,
    nInterpolationTerms = 0,
    ditherEnable = false,
    syncROMEnable = false,
    phaseAccEnable = true,
    roundingMode = RoundHalfUp,
    pincType = Streaming,
    poffType = Fixed
  )

  val paramsPLFG = FixedPLFGParams(
    maxNumOfSegments = 16,//4,
    maxNumOfDifferentChirps = 8,
    maxNumOfRepeatedChirps = 8,
    maxChirpOrdinalNum = 4,
    maxNumOfFrames = 4,
    maxNumOfSamplesWidth = 14,//12,
    outputWidthInt = 16,
    outputWidthFrac = 0
  )
  
  val paramsFFT = FFTParams.fixed(
    dataWidth = 16,
    twiddleWidth = 16,
    binPoint = 14,
    numPoints = 1024,
    numMulPipes = 1,
    numAddPipes = 1,
    decimType = DIFDecimType, // može i DIT ako je uključen bitReverse nebitno je
    useBitReverse = true,
    expandLogic = Array.fill(log2Up(1024))(0),
    keepMSBorLSB = Array.fill(log2Up(1024))(true),
    sdfRadix = "2^2"
  )


  
  it should "Test PLFG NCO FFT Chain" in {
    val lazyDut = LazyModule(new PLFGNCOFFTChain(paramsPLFG, paramsNCO, paramsFFT, AddressSet(0x001000, 0xFF), AddressSet(0x000000, 0x0FFF), AddressSet(0x001100, 0xFF), AddressSet(0x001200, 0xFF), beatBytes) {
    })
    chisel3.iotesters.Driver.execute(Array("-tiwv", "-tbn", "verilator", "-tivsuv"), () => lazyDut.module) {
      c => new DrawPLFGNCOFFTChainTester(lazyDut, AddressSet(0x001000, 0xFF), AddressSet(0x000000, 0x0FFF), AddressSet(0x001100, 0xFF), AddressSet(0x001200, 0xFF), beatBytes)
    } should be (true)
  }
  
}
