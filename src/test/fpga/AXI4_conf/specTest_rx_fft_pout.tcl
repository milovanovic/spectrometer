reset_hw_axi [get_hw_axis hw_axi_1]

# configure UART TX - enable TX
create_hw_axi_txn write_txn24 [get_hw_axis hw_axi_1] -type WRITE -address 30009008 -len 1 -data {00000001} 
run_hw_axi [get_hw_axi_txns write_txn24]
#configure UART RX - enable RX
create_hw_axi_txn write_txn25 [get_hw_axis hw_axi_1] -type WRITE -address 3000900C -len 1 -data {00000001} 
run_hw_axi [get_hw_axi_txns write_txn25]

# memWriteWord(params.fftSplitAddress.base + 0x0, 1) // set ready to OR this is necessary while ar is connected to rx_split
# configure fftSplitter to OR
create_hw_axi_txn write_txn9 [get_hw_axis hw_axi_1] -type WRITE -address 30004100 -len 1 -data {00000001} 
run_hw_axi [get_hw_axi_txns write_txn9]

# configure fft number of points - number of stages
create_hw_axi_txn write_txn10 [get_hw_axis hw_axi_1] -type WRITE -address 30004000 -len 1 -data {00000006} 
run_hw_axi [get_hw_axi_txns write_txn10]
#configure fftMuxAddress1
create_hw_axi_txn write_txn15 [get_hw_axis hw_axi_1] -type WRITE -address 30004124 -len 1 -data {00000000}
run_hw_axi [get_hw_axi_txns write_txn15]

#be sure that all spliters are connected to always ready block
#configure plfgMuxAddress0
create_hw_axi_txn write_txn17 [get_hw_axis hw_axi_1] -type WRITE -address 30002214 -len 1 -data {00000002}
run_hw_axi [get_hw_axi_txns write_txn17]
#configure ncoMuxAddress0
create_hw_axi_txn write_txn18 [get_hw_axis hw_axi_1] -type WRITE -address 30003110 -len 1 -data {00000002}
run_hw_axi [get_hw_axi_txns write_txn18]
#configure magMuxAddress0
create_hw_axi_txn write_txn19 [get_hw_axis hw_axi_1] -type WRITE -address 30005114 -len 1 -data {00000002}
run_hw_axi [get_hw_axi_txns write_txn19]
#configure fftMuxAddress0
create_hw_axi_txn write_txn20 [get_hw_axis hw_axi_1] -type WRITE -address 30004114 -len 1 -data {00000002}
run_hw_axi [get_hw_axi_txns write_txn20]
# configure outMuxAddress -> send on pout output
create_hw_axi_txn write_txn21 [get_hw_axis hw_axi_1] -type WRITE -address 30008000 -len 1 -data {00000002}
run_hw_axi [get_hw_axi_txns write_txn21]
# send always ready on URx split
create_hw_axi_txn write_txn22 [get_hw_axis hw_axi_1] -type WRITE -address 30008008 -len 1 -data {00000006}
run_hw_axi [get_hw_axi_txns write_txn22]
# configure divisor int
create_hw_axi_txn write_txn23 [get_hw_axis hw_axi_1] -type WRITE -address 30009018 -len 1 -data {00000364}  
run_hw_axi [get_hw_axi_txns write_txn23]


delete_hw_axi_txn [get_hw_axi_txns *]

# Important command for getting data with ila debug core
#write_hw_ila_data my_hw_ila_data_file.zip [upload_hw_ila_data hw_ila_1]

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

# // memWriteWord(params.inSplitAddress.base  + 0x0, 0) // set ready to AND
# // memWriteWord(params.ncoSplitAddress.base + 0x0, 0) // set ready to AND
# memWriteWord(params.fftSplitAddress.base + 0x0, 1) // set ready to OR
# // memWriteWord(params.magSplitAddress.base + 0x0, 0) // set ready to AND
# // memWriteWord(params.uRxSplitAddress.base + 0x0, 0) // set ready to AND

# // Mux  
# // memWriteWord(params.ncoMuxAddress1.base,       0x0) // output0
# // memWriteWord(params.ncoMuxAddress1.base + 0x4, 0x1) // output1 

# // memWriteWord(params.fftMuxAddress1.base,       0x1) // output0
# memWriteWord(params.fftMuxAddress1.base + 0x4, 0x0) // output1

# // memWriteWord(params.magMuxAddress1.base,       0x1) // output0
# // memWriteWord(params.magMuxAddress1.base + 0x4, 0x5) // output1

# // added - MP
# memWriteWord(params.plfgMuxAddress0.base + 0x4, 0x2) //rx split must have ready active

# memWriteWord(params.ncoMuxAddress0.base,       0x2) // output0 and s
# //memWriteWord(params.ncoMuxAddress0.base + 0x4, 0x2) // output1

# // memWriteWord(params.fftMuxAddress0.base,       0x5) // output0
# memWriteWord(params.fftMuxAddress0.base + 0x4, 0x1) // output1

# // memWriteWord(params.magMuxAddress0.base,       0x5) // output0
# memWriteWord(params.magMuxAddress0.base + 0x4, 0x2) // rx split must have ready active

# memWriteWord(params.outMuxAddress.base,       0x2) // output0
# // memWriteWord(params.outMuxAddress.base + 0x4, 0x2) // output1
# //memWriteWord(params.outMuxAddress.base + 0x8, 0x4) // output2

# // added - MP
# memWriteWord(params.outMuxAddress.base + 0x8, 0x6) //output 2 rxSplit must be set to ready

