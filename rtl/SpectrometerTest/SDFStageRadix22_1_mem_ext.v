module SDFStageRadix22_1_mem_ext(
  input  [7:0]  R0_addr,
  input         R0_clk,
  input         R0_en,
  output [31:0] R0_data,
  input  [7:0]  W0_addr,
  input         W0_en,
  input         W0_clk,
  input  [31:0] W0_data
);
  sky130_sram_1kbyte_1rw1r_32x256_8 #(.VERBOSE(0)) sram (
    .clk0  (W0_clk),    // input  - clock
    .csb0  (!W0_en),    // input  - active low chip select
    .web0  (!W0_en),    // input  - active low write control
    .wmask0(4'b1111),   // input  - write mask
    .addr0 (W0_addr),   // input  - addr
    .din0  (W0_data),   // input  - data
    .dout0 (),          // output - data
    .clk1  (R0_clk),    // input  - clock
    .csb1  (!R0_en),    // input  - active low chip select
    .addr1 (R0_addr),   // input  - addr
    .dout1 (R0_data)    // output - data
  );
endmodule