// SPDX-License-Identifier: Apache-2.0

package spectrometer

import chisel3._
import chisel3.experimental._
import chisel3.util._
import dspblocks._
import dsptools._
import dsptools.numbers._
import dspjunctions._
import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.config._
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper._
import freechips.rocketchip.tilelink._
import fft._
import plfg._
import nco._


class PLFGNCOFFTChain[T <: Data : Real : BinaryRepresentation]
(   
  paramsPLFG: PLFGParams[T],
  paramsNCO: NCOParams[T],
  paramsFFT: FFTParams[T],
  csrAddressPLFG: AddressSet,
  ramAddress: AddressSet,
  csrAddressNCO: AddressSet,
  csrAddressFFT: AddressSet,
  beatBytes: Int
) extends LazyModule()(Parameters.empty) {
    
  val PLFGModule = LazyModule(new PLFGDspBlockMem(csrAddressPLFG, ramAddress, paramsPLFG, beatBytes) {
  })
  
  val ncoModule = LazyModule(new AXI4NCOLazyModuleBlock(paramsNCO, csrAddressNCO, beatBytes) {
  })
  
  val fftModule = LazyModule(new AXI4FFTBlock(paramsFFT, csrAddressFFT, beatBytes))
  
  ncoModule.freq.get := PLFGModule.streamNode
  fftModule.streamNode := ncoModule.streamNode //.in(0)
  
  val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
  ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := fftModule.streamNode
  val outStream = InModuleBody { ioStreamNode.makeIO() }
      
  def standaloneParams = AXI4BundleParameters(addrBits = 32, dataBits = 32, idBits = 1)
  val topXbar = AXI4Xbar()
  PLFGModule.mem.get := topXbar
  //ncoModule.mem.get := topXbar
  fftModule.mem.get := topXbar

  val mem = Some(AXI4IdentityNode())
  topXbar := mem.get
  
  val ioMem = mem.map { m => {
    val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))

    m :=
    BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) :=
    ioMemNode

    val ioMem = InModuleBody { ioMemNode.makeIO() }
    ioMem
  }}

  lazy val module = new LazyModuleImp(this)
    
}


object PLFGNCOFFTChainApp extends App {

  val beatBytes = 4

  val paramsNCO = FixedNCOParams(
    tableSize = 128,
    tableWidth = 16,
    phaseWidth = 9,
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
    maxNumOfSegments = 4,
    maxNumOfDifferentChirps = 8,
    maxNumOfRepeatedChirps = 8,
    maxChirpOrdinalNum = 4,
    maxNumOfFrames = 4,
    maxNumOfSamplesWidth = 8,
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
    decimType = DIFDecimType,
    useBitReverse = true,
    expandLogic = Array.fill(log2Up(1024))(0),
    keepMSBorLSB = Array.fill(log2Up(1024))(true),
    sdfRadix = "2^2"
  )
  
  val chainModule = LazyModule(new PLFGNCOFFTChain(paramsPLFG, paramsNCO, paramsFFT, AddressSet(0x001000, 0xFF), AddressSet(0x000000, 0x0FFF), AddressSet(0x001100, 0xFF), AddressSet(0x001200, 0xFF), beatBytes) {
  })

  chisel3.Driver.execute(Array("--target-dir", "verilog", "--top-name", "ChainApp"), ()=> chainModule.module) // generate verilog code
}
