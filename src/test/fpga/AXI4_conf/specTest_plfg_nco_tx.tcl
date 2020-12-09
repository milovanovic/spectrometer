# SPDX-License-Identifier: Apache-2.0

reset_hw_axi [get_hw_axis hw_axi_1]

# configure plfg 
create_hw_axi_txn write_txn1 [get_hw_axis hw_axi_1] -type WRITE -address 30001000 -len 1 -data {24000000}
run_hw_axi [get_hw_axi_txns write_txn1]

# configure number of frames
create_hw_axi_txn write_txn2 [get_hw_axis hw_axi_1] -type WRITE -address 30002108 -len 1 -data {00000004}
run_hw_axi [get_hw_axi_txns write_txn2]
# configure number of chirps 30002100
create_hw_axi_txn write_txn3 [get_hw_axis hw_axi_1] -type WRITE -address 30002110 -len 1 -data {00000001}
run_hw_axi [get_hw_axi_txns write_txn3]
# configure start value - set to 16 - 10!
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

#configure plfgMuxAddress1
create_hw_axi_txn write_txn13 [get_hw_axis hw_axi_1] -type WRITE -address 30002220 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn13]
#configure ncoMuxAddress1
create_hw_axi_txn write_txn14 [get_hw_axis hw_axi_1] -type WRITE -address 30003124 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn14]

#configure plfgMuxAddress0
create_hw_axi_txn write_txn17 [get_hw_axis hw_axi_1] -type WRITE -address 30002210 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn17]
#configure ncoMuxAddress0
create_hw_axi_txn write_txn18 [get_hw_axis hw_axi_1] -type WRITE -address 30003114 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn18]


#configure outMuxAddress -> send on UART
create_hw_axi_txn write_txn21 [get_hw_axis hw_axi_1] -type WRITE -address 30008004 -len 1 -data {00000003}
run_hw_axi [get_hw_axi_txns write_txn21]

# configure divisor int - 868 -> 0364! 869 ->0365
create_hw_axi_txn write_txn22 [get_hw_axis hw_axi_1] -type WRITE -address 30009018 -len 1 -data {00000364}  
run_hw_axi [get_hw_axi_txns write_txn22]

# configure UART TX - enable TX
create_hw_axi_txn write_txn23 [get_hw_axis hw_axi_1] -type WRITE -address 30009008 -len 1 -data {00000001} 
run_hw_axi [get_hw_axi_txns write_txn23]

delete_hw_axi_txn [get_hw_axi_txns *]

# Equivalent test in Scala
  
# val segmentNumsArrayOffset = 6 * params.beatBytes
# val repeatedChirpNumsArrayOffset = segmentNumsArrayOffset + 4 * params.beatBytes
# val chirpOrdinalNumsArrayOffset = repeatedChirpNumsArrayOffset + 8 * params.beatBytes

# memWriteWord(params.plfgRAM.base, 0x24001004)
# memWriteWord(params.plfgAddress.base + 2*params.beatBytes, 4)            // number of frames
# memWriteWord(params.plfgAddress.base + 4*params.beatBytes, 1)            // number of chirps
# memWriteWord(params.plfgAddress.base + 5*params.beatBytes, 16)           // start value
# memWriteWord(params.plfgAddress.base + segmentNumsArrayOffset, 1)        // number of segments for first chirp
# memWriteWord(params.plfgAddress.base + repeatedChirpNumsArrayOffset, 1)  // determines number of repeated chirps
# memWriteWord(params.plfgAddress.base + chirpOrdinalNumsArrayOffset, 0) 
# memWriteWord(params.plfgAddress.base + params.beatBytes, 0)              // set reset bit to zero
# memWriteWord(params.plfgAddress.base, 1)                                 // enable bit becomes 1

# memWriteWord(params.plfgMuxAddress1.base,       0x0) // output0
# memWriteWord(params.ncoMuxAddress1.base,       0x0) // output0
# memWriteWord(params.fftMuxAddress1.base + 0x4, 0x0) // output1

# memWriteWord(params.plfgMuxAddress0.base,       0x0) // output0
# memWriteWord(params.ncoMuxAddress0.base,       0x0) // output0
# memWriteWord(params.fftMuxAddress0.base + 0x4, 0x0) // output1

# memWriteWord(params.outMuxAddress.base + 0x4,       0x2) // output0
