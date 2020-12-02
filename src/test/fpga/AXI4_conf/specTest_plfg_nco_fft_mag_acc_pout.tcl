reset_hw_axi [get_hw_axis hw_axi_1]
# configure jpl magnitude
create_hw_axi_txn write_txn10 [get_hw_axis hw_axi_1] -type WRITE -address 30005000 -len 1 -data {00000002} 
run_hw_axi [get_hw_axi_txns write_txn10]

# configure plfg 
create_hw_axi_txn write_txn1 [get_hw_axis hw_axi_1] -type WRITE -address 30001000 -len 1 -data {24000000}
run_hw_axi [get_hw_axi_txns write_txn1]

# configure number of frames
create_hw_axi_txn write_txn2 [get_hw_axis hw_axi_1] -type WRITE -address 30002108 -len 1 -data {00000004}
run_hw_axi [get_hw_axi_txns write_txn2]
# configure number of chirps 30002100
create_hw_axi_txn write_txn3 [get_hw_axis hw_axi_1] -type WRITE -address 30002110 -len 1 -data {00000001}
run_hw_axi [get_hw_axi_txns write_txn3]
# configure start value - set to 16!
create_hw_axi_txn write_txn4 [get_hw_axis hw_axi_1] -type WRITE -address 30002114 -len 1 -data {00000010}
run_hw_axi [get_hw_axi_txns write_txn4]
# configure number of segments for first chirp
create_hw_axi_txn write_txn5 [get_hw_axis hw_axi_1] -type WRITE -address 30002118 -len 1 -data {00000001}
run_hw_axi [get_hw_axi_txns write_txn5]
# configure number of repeated chirps
create_hw_axi_txn write_txn6 [get_hw_axis hw_axi_1] -type WRITE -address 30002128 -len 1 -data {00000001}
run_hw_axi [get_hw_axi_txns write_txn6]
# chip ordinal
create_hw_axi_txn write_txn7 [get_hw_axis hw_axi_1] -type WRITE -address 30002148 -len 1 -data {00000000} 
run_hw_axi [get_hw_axi_txns write_txn7]
# set reset bit to zero
create_hw_axi_txn write_txn8 [get_hw_axis hw_axi_1] -type WRITE -address 30002100 -len 1 -data {00000001} 
run_hw_axi [get_hw_axi_txns write_txn8]
# configure fft number of points - number of stages
create_hw_axi_txn write_txn9 [get_hw_axis hw_axi_1] -type WRITE -address 30004000 -len 1 -data {00000006} 
run_hw_axi [get_hw_axi_txns write_txn9]
# configure register acc depth - should be the same as fft size - 64
create_hw_axi_txn write_txn11 [get_hw_axis hw_axi_1] -type WRITE -address 30007000 -len 1 -data {00000040}
run_hw_axi [get_hw_axi_txns write_txn11]
# configure number of accumulated fft windows
create_hw_axi_txn write_txn12 [get_hw_axis hw_axi_1] -type WRITE -address 30007004 -len 1 -data {00000004} 
run_hw_axi [get_hw_axi_txns write_txn12]



#configure plfgMuxAddress1
create_hw_axi_txn write_txn13 [get_hw_axis hw_axi_1] -type WRITE -address 30002220 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn13]
#configure ncoMuxAddress1
create_hw_axi_txn write_txn14 [get_hw_axis hw_axi_1] -type WRITE -address 30003120 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn14]
#configure fftMuxAddress1
create_hw_axi_txn write_txn15 [get_hw_axis hw_axi_1] -type WRITE -address 30004120 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn15]
#configure magMuxAddress1
create_hw_axi_txn write_txn16 [get_hw_axis hw_axi_1] -type WRITE -address 30005120 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn16]
#configure plfgMuxAddress0
create_hw_axi_txn write_txn17 [get_hw_axis hw_axi_1] -type WRITE -address 30002210 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn17]
#configure ncoMuxAddress0
create_hw_axi_txn write_txn18 [get_hw_axis hw_axi_1] -type WRITE -address 30003110 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn18]
#configure fftMuxAddress0
create_hw_axi_txn write_txn19 [get_hw_axis hw_axi_1] -type WRITE -address 30004110 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn19]
#configure magMuxAddress0
create_hw_axi_txn write_txn20 [get_hw_axis hw_axi_1] -type WRITE -address 30005110 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn20]
#configure outMuxAddress
create_hw_axi_txn write_txn21 [get_hw_axis hw_axi_1] -type WRITE -address 30008000 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn21]

delete_hw_axi_txn [get_hw_axi_txns *]

# Equivalent test in Scala
# val numAccuWin = 4 // Number of accumulator windows
# //startingPoint * (numOfPoints / (4*tableSize))
# // plfg setup
# val segmentNumsArrayOffset = 6 * params.beatBytes
# val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
# val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes

# memWriteWord(params.plfgRAM.base, 0x24000000)
# memWriteWord(params.plfgAddress.base + 2*params.beatBytes, numAccuWin*2) // number of frames
# memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
# //memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 1)          // start value
# // for numPoints equal to 64 
# memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 16)            // start value
# //memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 4)            // start value
# memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
# memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
# memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
# memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
# memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1

# // Mux
# memWriteWord(params.plfgMuxAddress1.base,0x0) // output0
# memWriteWord(params.ncoMuxAddress1.base, 0x0) // output0
# memWriteWord(params.fftMuxAddress1.base, 0x0) // output0
# memWriteWord(params.magMuxAddress1.base, 0x0) // output0
# memWriteWord(params.plfgMuxAddress0.base,0x0) // output0
# memWriteWord(params.ncoMuxAddress0.base, 0x0) // output0
# memWriteWord(params.fftMuxAddress0.base, 0x0) // output0
# memWriteWord(params.magMuxAddress0.base, 0x0) // output0
# memWriteWord(params.outMuxAddress.base,  0x0) // output0
# memWriteWord(params.magAddress.base, 0x2) // set jpl magnitude
# memWriteWord(params.accAddress.base, params.fftParams.numPoints)  // set number of fft points
# memWriteWord(params.accAddress.base + 0x4, numAccuWin)            // set number of accumulator windows
  
# Address space for spectrometer test with buffers  

# inSplitAddress   = AddressSet(0x30000000, 0xF),
# plfgRAM          = AddressSet(0x30001000, 0xFFF),
# plfgAddress      = AddressSet(0x30002100, 0xFF),
# plfgSplitAddress = AddressSet(0x30002200, 0xF),
# plfgMuxAddress0  = AddressSet(0x30002210, 0xF),
# plfgMuxAddress1  = AddressSet(0x30002220, 0xF),
# ncoAddress       = AddressSet(0x30003000, 0xF),
# ncoSplitAddress  = AddressSet(0x30003100, 0xF),
# ncoMuxAddress0   = AddressSet(0x30003110, 0xF),
# ncoMuxAddress1   = AddressSet(0x30003120, 0xF),
# fftAddress       = AddressSet(0x30004000, 0xFF),
# fftSplitAddress  = AddressSet(0x30004100, 0xF),
# fftMuxAddress0   = AddressSet(0x30004110, 0xF),
# fftMuxAddress1   = AddressSet(0x30004120, 0xF),
# magAddress       = AddressSet(0x30005000, 0xFF),
# magSplitAddress  = AddressSet(0x30005100, 0xF),
# magMuxAddress0   = AddressSet(0x30005110, 0xF),
# magMuxAddress1   = AddressSet(0x30005120, 0xF),
# accQueueBase     =            0x30006000,
# accAddress       = AddressSet(0x30007000, 0xF),
# outMuxAddress    = AddressSet(0x30008000, 0xF),
# uartParams       = UARTParams(address = 0x30009000, nTxEntries = 256, nRxEntries = 256),
# uRxSplitAddress  = AddressSet(0x30009100, 0xF)