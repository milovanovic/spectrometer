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
import freechips.rocketchip.config.{Parameters, Field, Config}
import freechips.rocketchip.diplomacy._
import freechips.rocketchip.util._

import plfg._
import nco._
import fft._
import uart._
import splitter._
import magnitude._
import accumulator._


case class SpectrometerTestWithBuffersParameters (
    plfgParams      : PLFGParams[FixedPoint],
    ncoParams       : NCOParams[FixedPoint],
    fftParams       : FFTParams[FixedPoint],
    magParams       : MAGParams[FixedPoint],
    accParams       : AccParams[FixedPoint],
    inSplitAddress  : AddressSet,
    plfgAddress     : AddressSet,
    plfgRAM         : AddressSet,
    ncoAddress      : AddressSet,
    ncoSplitAddress : AddressSet,
    ncoMuxAddress0  : AddressSet,
    ncoMuxAddress1  : AddressSet,
    fftAddress      : AddressSet,
    fftRAM          : AddressSet,
    fftSplitAddress : AddressSet,
    fftMuxAddress0  : AddressSet,
    fftMuxAddress1  : AddressSet,
    magAddress      : AddressSet,
    magSplitAddress : AddressSet,
    magMuxAddress0  : AddressSet,
    magMuxAddress1  : AddressSet,
    accAddress      : AddressSet,
    accQueueBase    : BigInt,
    outMuxAddress   : AddressSet,
    uartParams      : UARTParams,
    uRxSplitAddress : AddressSet,
    divisorInit     : Int,
    beatBytes       : Int
)

class SpectrometerTestWithBuffers(params: SpectrometerTestWithBuffersParameters) extends LazyModule()(Parameters.empty) {

  val in_adapt  = AXI4StreamWidthAdapter.nToOne(params.beatBytes)
  val in_split  = LazyModule(new AXI4Splitter(address = params.inSplitAddress, beatBytes = params.beatBytes))
  val in_queue  = LazyModule(new StreamBuffer(BufferParams(1, true, true), beatBytes = 1))

  val plfg      = LazyModule(new PLFGDspBlockMem(params.plfgAddress, params.plfgRAM, params.plfgParams, params.beatBytes))  
  val nco       = LazyModule(new AXI4NCOLazyModuleBlock(params.ncoParams, params.ncoAddress, params.beatBytes))
  val nco_split = LazyModule(new AXI4Splitter(address  = params.ncoSplitAddress, beatBytes = params.beatBytes))
  val nco_mux_0 = LazyModule(new AXI4StreamMux(address = params.ncoMuxAddress0,  beatBytes = params.beatBytes))
  val nco_mux_1 = LazyModule(new AXI4StreamMux(address = params.ncoMuxAddress1,  beatBytes = params.beatBytes))
  val nco_rdy_1 = LazyModule(new AlwaysReady)
  val nco_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val nco_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val nco_rdy_0 = LazyModule(new AlwaysReady)
  
  val fft       = LazyModule(new AXI4FFTBlock(address = params.fftAddress, params = params.fftParams, _beatBytes = params.beatBytes)) 
  val fft_split = LazyModule(new AXI4Splitter(address  = params.fftSplitAddress, beatBytes = params.beatBytes))
  val fft_mux_0 = LazyModule(new AXI4StreamMux(address = params.fftMuxAddress0,  beatBytes = params.beatBytes))
  val fft_mux_1 = LazyModule(new AXI4StreamMux(address = params.fftMuxAddress1,  beatBytes = params.beatBytes))
  val fft_rdy_1 = LazyModule(new AlwaysReady)
  val fft_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val fft_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val fft_rdy_0 = LazyModule(new AlwaysReady)

  val mag       = LazyModule(new AXI4LogMagMuxBlock(params.magParams, params.magAddress, _beatBytes = params.beatBytes))
  val mag_split = LazyModule(new AXI4Splitter(address  = params.magSplitAddress, beatBytes = params.beatBytes))
  val mag_mux_0 = LazyModule(new AXI4StreamMux(address = params.magMuxAddress0,  beatBytes = params.beatBytes))
  val mag_mux_1 = LazyModule(new AXI4StreamMux(address = params.magMuxAddress1,  beatBytes = params.beatBytes))
  val mag_rdy_1 = LazyModule(new AlwaysReady)
  val mag_ones  = LazyModule(new AllOnes(beatBytes = params.beatBytes))
  val mag_zeros = LazyModule(new AllZeros(beatBytes = params.beatBytes))
  val mag_rdy_0 = LazyModule(new AlwaysReady)

  val acc       = LazyModule(new AccumulatorChain(params.accParams, params.accAddress, params.accQueueBase, params.beatBytes))
  val acc_adapt = AXI4StreamWidthAdapter.nToOne(params.beatBytes/2)
  val acc_queue = LazyModule(new StreamBuffer(BufferParams(1, true, true), beatBytes = 4))

  val out_mux   = LazyModule(new AXI4StreamMux(address = params.outMuxAddress, beatBytes = params.beatBytes))
  val out_queue = LazyModule(new StreamBuffer(BufferParams(1, true, true), beatBytes = params.beatBytes))
  val out_adapt = AXI4StreamWidthAdapter.oneToN(params.beatBytes)
  val out_rdy   = LazyModule(new AlwaysReady)

  val uTx_queue = LazyModule(new StreamBuffer(BufferParams(params.beatBytes), beatBytes = params.beatBytes))
  val uTx_adapt = AXI4StreamWidthAdapter.oneToN(params.beatBytes)
  val uRx_adapt = AXI4StreamWidthAdapter.nToOne(params.beatBytes)
  val uRx_split = LazyModule(new AXI4Splitter(address = params.uRxSplitAddress, beatBytes = params.beatBytes))
  val uart      = LazyModule(new AXI4UARTBlock(params.uartParams, AddressSet(params.uartParams.address,0xFF), divisorInit = params.divisorInit, _beatBytes = params.beatBytes){
    // Add interrupt bundle
    val ioIntNode = BundleBridgeSink[Vec[Bool]]()
    ioIntNode := IntToBundleBridge(IntSinkPortParameters(Seq(IntSinkParameters()))) := intnode
    val ioInt = InModuleBody {
        val io = IO(Output(ioIntNode.bundle.cloneType))
        io.suggestName("int")
        io := ioIntNode.bundle
        io
    }
  })

  // define mem
  lazy val blocks = Seq(in_split, plfg, nco, nco_split, nco_mux_0, nco_mux_1, fft, fft_split, fft_mux_0, fft_mux_1, mag, mag_split, mag_mux_0, mag_mux_1, out_mux, uart, uRx_split)
  val bus = LazyModule(new AXI4Xbar)
  val mem = Some(bus.node)
  for (b <- blocks) {
    b.mem.foreach { _ := AXI4Buffer() :=  bus.node }
  } 
  acc.mem.get := bus.node

  // connect nodes
  in_split.streamNode  := in_adapt := in_queue.node
  
  nco.freq.get         := plfg.streamNode          // plfg       -----> nco

  nco_split.streamNode := nco.streamNode           // nco        -----> nco_split
  nco_mux_1.streamNode := nco_split.streamNode     // nco_split  -----> nco_mux_1  
  nco_rdy_1.streamNode := nco_mux_1.streamNode     // nco_mux_1  --0--> nco_rdy_1

  nco_mux_0.streamNode := nco_split.streamNode      // nco_split --0--> nco_mux_0
  nco_mux_0.streamNode := in_split.streamNode       // in_split  --1--> nco_mux_0
  nco_mux_0.streamNode := uRx_split.streamNode      // uRx_split --2--> nco_mux_0
  nco_mux_0.streamNode := nco_ones.streamNode       // nco_ones  --3--> nco_mux_0
  nco_mux_0.streamNode := nco_zeros.streamNode      // nco_zeros --4--> nco_mux_0
  fft.streamNode       := AXI4StreamBuffer() := nco_mux_0.streamNode      // nco_mux_0 --0--> fft
  nco_rdy_0.streamNode := nco_mux_0.streamNode      // nco_mux_0 --1--> nco_rdy_0

  fft_split.streamNode := fft.streamNode            // fft       --0--> fft_split  
  fft_mux_1.streamNode := fft_split.streamNode      // fft_split -----> fft_mux_1  
  fft_rdy_1.streamNode := fft_mux_1.streamNode      // fft_mux_1 --0--> fft_rdy_1

  fft_mux_0.streamNode := fft_split.streamNode      // fft_split --0--> fft_mux_0
  fft_mux_0.streamNode := in_split.streamNode       // in_split  --1--> fft_mux_0
  fft_mux_0.streamNode := uRx_split.streamNode      // uRx_split --2--> fft_mux_0
  fft_mux_0.streamNode := fft_ones.streamNode       // fft_ones  --3--> fft_mux_0
  fft_mux_0.streamNode := fft_zeros.streamNode      // fft_zeros --4--> fft_mux_0
  mag.streamNode       := AXI4StreamBuffer() := fft_mux_0.streamNode      // fft_mux_0 --0--> mag
  fft_rdy_0.streamNode := fft_mux_0.streamNode      // fft_mux_0 --1--> fft_rdy_0

  mag_split.streamNode := mag.streamNode            // mag       --0--> mag_split  
  mag_mux_1.streamNode := mag_split.streamNode      // mag_split -----> mag_mux_1  
  mag_rdy_1.streamNode := mag_mux_1.streamNode      // mag_mux_1 --0--> mag_rdy_1

  mag_mux_0.streamNode := mag_split.streamNode      // mag_split --0--> mag_mux_0
  mag_mux_0.streamNode := in_split.streamNode       // in_split  --1--> mag_mux_0
  mag_mux_0.streamNode := uRx_split.streamNode      // uRx_split --2--> mag_mux_0
  mag_mux_0.streamNode := mag_ones.streamNode       // mag_ones  --3--> mag_mux_0
  mag_mux_0.streamNode := mag_zeros.streamNode      // mag_zeros --4--> mag_mux_0
  acc.streamNode       := AXI4StreamBuffer() := mag_mux_0.streamNode      // mag_mux_0 --0--> acc
  mag_rdy_0.streamNode := mag_mux_0.streamNode      // mag_mux_0 --1--> mag_rdy_0

  acc_queue.node := acc_adapt := acc.streamNode

  out_mux.streamNode   := acc_queue.node            // acc       --0--> out_mux
  out_mux.streamNode   := AXI4StreamBuffer() := mag_mux_1.streamNode      // mag_mux_1 --1--> out_mux
  out_mux.streamNode   := AXI4StreamBuffer() := fft_mux_1.streamNode      // fft_mux_1 --2--> out_mux
  out_mux.streamNode   := AXI4StreamBuffer() := nco_mux_1.streamNode      // nco_mux_1 --3--> out_mux
  out_mux.streamNode   := AXI4StreamBuffer() := in_split.streamNode       // in_split  --4--> out_mux
  out_mux.streamNode   := AXI4StreamBuffer() := uRx_split.streamNode      // uRx_split --5--> out_mux
  out_queue.node       := AXI4StreamBuffer() := out_mux.streamNode        // out_mux   --0--> out_queue
  uTx_adapt := uTx_queue.node := out_mux.streamNode // out_mux   --1--> uTx_queue -----> uTx_adapt  
  out_rdy.streamNode   := out_mux.streamNode        // out_mux   --2--> out_rdy

  out_adapt := out_queue.node                       // out_queue ----> out_adapt

  uRx_adapt := uart.streamNode := uTx_adapt         // uTx_adapt -----> uart      -----> uRx_adapt
  uRx_split.streamNode := uRx_adapt                 // uRx_adapt -----> uRx_split

  lazy val module = new LazyModuleImp(this) {

    // generate interrupt output
    val int = IO(Output(uart.ioInt.cloneType))
    int := uart.ioInt

    // generate uart input/output
    val uTx = IO(Output(uart.module.io.txd.cloneType))
    val uRx = IO(Input(uart.module.io.rxd.cloneType))

    uTx := uart.module.io.txd
    uart.module.io.rxd := uRx
  }
}

object SpectrometerTestWithBuffersApp extends App
{
  // here just define parameters
  val params = SpectrometerTestWithBuffersParameters (
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
    inSplitAddress  = AddressSet(0x30000010, 0xF),
    plfgAddress     = AddressSet(0x30000200, 0xFF),
    plfgRAM         = AddressSet(0x30001000, 0xFFF),
    ncoAddress      = AddressSet(0x30000020, 0xF),
    ncoSplitAddress = AddressSet(0x30000030, 0xF),
    ncoMuxAddress0  = AddressSet(0x30000040, 0xF),
    ncoMuxAddress1  = AddressSet(0x30000050, 0xF),
    fftAddress      = AddressSet(0x30000300, 0xFF),
    fftRAM          = AddressSet(0x30002000, 0xFFF),
    fftSplitAddress = AddressSet(0x30000060, 0xF),
    fftMuxAddress0  = AddressSet(0x30000070, 0xF),
    fftMuxAddress1  = AddressSet(0x30000080, 0xF),
    magAddress      = AddressSet(0x30000400, 0xFF),
    magSplitAddress = AddressSet(0x30000090, 0xF),
    magMuxAddress0  = AddressSet(0x300000A0, 0xF),
    magMuxAddress1  = AddressSet(0x300000B0, 0xF),
    accAddress      = AddressSet(0x300000C0, 0xF),
    accQueueBase    = 0x30004000,
    outMuxAddress   = AddressSet(0x300000E0, 0xF),
    uartParams      = UARTParams(address = 0x30000700, nTxEntries = 256, nRxEntries = 256),
    uRxSplitAddress = AddressSet(0x30000110, 0xF),
    divisorInit    = (2).toInt,
    beatBytes      = 4)

  implicit val p: Parameters = Parameters.empty
  val standaloneModule = LazyModule(new SpectrometerTestWithBuffers(params) {

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
    ioStreamNode := AXI4StreamToBundleBridge(AXI4StreamSlaveParameters()) := out_adapt
    val outStream = InModuleBody { ioStreamNode.makeIO() }

    val ioparallelin = BundleBridgeSource(() => new AXI4StreamBundle(AXI4StreamBundleParameters(n = 1)))
    in_queue.node := BundleBridgeToAXI4Stream(AXI4StreamMasterParameters(n = 1)) := ioparallelin
    val inStream = InModuleBody { ioparallelin.makeIO() }
  })
  chisel3.Driver.execute(Array("--target-dir", "./verilog/rtl", "--top-name", "SpectrometerTestWithBuffers"), ()=> standaloneModule.module) // generate verilog code
}
