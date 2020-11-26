package spectrometer

import dspblocks._
import dsptools._
import dsptools.numbers._

import chisel3._
import chisel3.experimental._
import chisel3.util._

import freechips.rocketchip.amba.axi4._
import freechips.rocketchip.amba.axi4stream._
import freechips.rocketchip.interrupts._
import freechips.rocketchip.subsystem.{BaseSubsystem, CrossingWrapper}
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.regmapper.{HasRegMap, RegField}
import freechips.rocketchip.tilelink._
import freechips.rocketchip.util.UIntIsOneOf
import freechips.rocketchip.util._

import plfg._
import nco._
import fft._
import magnitude._
import accumulator._


case class SpectrometerVanillaParameters (
    plfgParams      : PLFGParams[FixedPoint],
    ncoParams       : NCOParams[FixedPoint],
    fftParams       : FFTParams[FixedPoint],
    magParams       : MAGParams[FixedPoint],
    accParams       : AccParams[FixedPoint],
    plfgAddress     : AddressSet,
    plfgRAM         : AddressSet,
    ncoAddress      : AddressSet,
    fftAddress      : AddressSet,
    magAddress      : AddressSet,
    accAddress      : AddressSet,
    accQueueBase    : BigInt,
    beatBytes       : Int
)

class SpectrometerVanilla(params: SpectrometerVanillaParameters) extends LazyModule()(Parameters.empty) {

  val plfg      = LazyModule(new PLFGDspBlockMem(params.plfgAddress, params.plfgRAM, params.plfgParams, params.beatBytes))  
  val nco       = LazyModule(new AXI4NCOLazyModuleBlock(params.ncoParams, params.ncoAddress, params.beatBytes))
  val buff_nco  = LazyModule(new AXI4StreamBuffer(BufferParams(2, false, false), beatBytes = 4))
  val fft       = LazyModule(new AXI4FFTBlock(address = params.fftAddress, params = params.fftParams, _beatBytes = params.beatBytes))
  val buff_fft  = LazyModule(new AXI4StreamBuffer(BufferParams(2, false, false), beatBytes = 4))
  val mag       = LazyModule(new AXI4LogMagMuxBlock(params.magParams, params.magAddress, _beatBytes = params.beatBytes))
  val buff_mag  = LazyModule(new AXI4StreamBuffer(BufferParams(2, false, false), beatBytes = 4))
  val acc       = LazyModule(new AccumulatorChain(params.accParams, params.accAddress, params.accQueueBase, params.beatBytes))

  // define mem
  lazy val blocks = Seq(plfg, nco, fft, mag)
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
  for (b <- blocks) {
    b.mem.foreach { _ := AXI4Buffer() := bus.node }
    //b.mem.foreach { _ := bus.node }
  }
  acc.mem.get := bus.node

  // connect nodes
  nco.freq.get := plfg.streamNode
  //acc.streamNode := mag.streamNode := buff_fft.node := fft.streamNode := buff_nco.node := nco.streamNo
  acc.streamNode := buff_mag.node := mag.streamNode := buff_fft.node := fft.streamNode := buff_nco.node := nco.streamNode
  
  lazy val module = new LazyModuleImp(this) {}
}

object SpectrometerVanillaApp extends App
{
  // here just define parameters
  val params = SpectrometerVanillaParameters (
    plfgParams = FixedPLFGParams(
      maxNumOfSegments = 4,
      maxNumOfDifferentChirps = 8,
      maxNumOfRepeatedChirps = 8,
      maxChirpOrdinalNum = 4,
      maxNumOfFrames = 4,
      maxNumOfSamplesWidth = 8,
      outputWidthInt = 16,
      outputWidthFrac = 0
    ),
    ncoParams = FixedNCOParams(
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
	  ),
    fftParams = FFTParams.fixed(
      dataWidth = 16,
      twiddleWidth = 16,
      numPoints = 1024,
      useBitReverse  = false,
      runTime = true,
      numAddPipes = 1,
      numMulPipes = 1,
      expandLogic = Array.fill(log2Up(1024))(0),
      keepMSBorLSB = Array.fill(log2Up(1024))(true),
      minSRAMdepth = 1024,
      binPoint = 0
    ),
    magParams = MAGParams.fixed(
      dataWidth       = 16,
      binPoint        = 0,
      dataWidthLog    = 16,
      binPointLog     = 9,
      log2LookUpWidth = 9,
      useLast         = true,
      numAddPipes     = 1,
      numMulPipes     = 1
    ),
    accParams = AccParams(
      proto    = FixedPoint(16.W, 0.BP),
      protoAcc = FixedPoint(32.W, 0.BP),
    ),
    plfgAddress     = AddressSet(0x30000000, 0xFF),
    plfgRAM         = AddressSet(0x30001000, 0xFFF),
    ncoAddress      = AddressSet(0x30000300, 0xF),
    fftAddress      = AddressSet(0x30000100, 0xFF),
    magAddress      = AddressSet(0x30000200, 0xFF),
    accAddress      = AddressSet(0x30000310, 0xF),
    accQueueBase    = 0x30002000,
    beatBytes      = 4)

  implicit val p: Parameters = Parameters.empty
  val standaloneModule = LazyModule(new SpectrometerVanilla(params) {

    // Generate AXI4 slave output
    def standaloneParams = AXI4BundleParameters(addrBits = params.beatBytes*8, dataBits = params.beatBytes*8, idBits = 1)
    val ioMem = mem.map { m => {
      val ioMemNode = BundleBridgeSource(() => AXI4Bundle(standaloneParams))
      m := BundleBridgeToAXI4(AXI4MasterPortParameters(Seq(AXI4MasterParameters("bundleBridgeToAXI4")))) := ioMemNode
      val ioMem = InModuleBody { ioMemNode.makeIO() }
      ioMem
    }}

    // Generate AXI-stream output
    val ioStreamNode = BundleBridgeSink[AXI4StreamBundle]()
    ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := acc.streamNode
    val outStream = InModuleBody { ioStreamNode.makeIO() }
  })
  chisel3.Driver.execute(Array("--target-dir", "verilog/SpectrometerVanilla", "--top-name", "SpectrometerVanilla"), ()=> standaloneModule.module) // generate verilog code
}

