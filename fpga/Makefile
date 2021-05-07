#########################################################################################
# fpga prototype makefile
#########################################################################################

#########################################################################################
# general path variables
#########################################################################################
base_dir=$(abspath ..)
sim_dir=$(abspath .)

# do not generate simulation files
sim_name := none

#########################################################################################
# include shared variables
#########################################################################################

VIVADO_VERSION := $(shell vivado -version | head -1 | grep -o -P '(?<=\s).*(?=\s)')

SUB_PROJECT ?= vcu118

ifeq ($(SUB_PROJECT),vcu118)
	SBT_PROJECT       ?= fpga_platforms
	MODEL             ?= VCU118FPGATestHarness
	VLOG_MODEL        ?= VCU118FPGATestHarness
	MODEL_PACKAGE     ?= chipyard.fpga.vcu118
	CONFIG            ?= RocketVCU118Config
	CONFIG_PACKAGE    ?= chipyard.fpga.vcu118
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= vcu118
	FPGA_BRAND        ?= xilinx
endif

ifeq ($(SUB_PROJECT),bringup)
	SBT_PROJECT       ?= fpga_platforms
	MODEL             ?= BringupVCU118FPGATestHarness
	VLOG_MODEL        ?= BringupVCU118FPGATestHarness
	MODEL_PACKAGE     ?= chipyard.fpga.vcu118.bringup
	CONFIG            ?= RocketBringupConfig
	CONFIG_PACKAGE    ?= chipyard.fpga.vcu118.bringup
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= vcu118
	FPGA_BRAND        ?= xilinx
endif

ifeq ($(SUB_PROJECT),arty)
	# TODO: Fix with Arty
	SBT_PROJECT       ?= fpga_platforms
	MODEL             ?= ArtyFPGATestHarness
	VLOG_MODEL        ?= ArtyFPGATestHarness
	MODEL_PACKAGE     ?= chipyard.fpga.arty
	CONFIG            ?= TinyRocketArtyConfig
	CONFIG_PACKAGE    ?= chipyard.fpga.arty
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= none # unused
	TOP               ?= ChipTop
	BOARD             ?= arty
	FPGA_FAMILY       ?= kintex7
	FPGA_BRAND        ?= xilinx
endif

ifeq ($(SUB_PROJECT),artysim)
	# TODO: Fix with Arty
	SBT_PROJECT       ?= fpga_platforms
	MODEL             ?= ArtyFPGATestHarness
	VLOG_MODEL        ?= ArtyFPGATestHarness
	MODEL_PACKAGE     ?= chipyard.fpga.arty
	CONFIG            ?= TinyRocketArtySimConfig
	CONFIG_PACKAGE    ?= chipyard.fpga.arty
	GENERATOR_PACKAGE ?= chipyard
	TB                ?= ArtyTestDriver
	TOP               ?= ChipTop
	BOARD             ?= arty
	FPGA_FAMILY       ?= kintex7
	FPGA_BRAND        ?= xilinx
	sim_name           = arty
endif

include $(base_dir)/variables.mk

# default variables to build the arty example
# setup the board to use

.PHONY: default
default: $(mcs)

#########################################################################################
# misc. directories
#########################################################################################
fpga_dir := $(base_dir)/fpga/fpga-shells/$(FPGA_BRAND)
fpga_common_script_dir := $(fpga_dir)/common/tcl

#########################################################################################
# import other necessary rules and variables
#########################################################################################
include $(base_dir)/common.mk

#########################################################################################
# copy from other directory
#########################################################################################
all_vsrcs := \
	$(sim_vsrcs) \
	$(base_dir)/generators/sifive-blocks/vsrc/SRLatch.v \
	$(fpga_dir)/common/vsrc/PowerOnResetFPGAOnly.v

#########################################################################################
# vivado rules
#########################################################################################
# combine all sources into single .f
synth_list_f := $(build_dir)/$(long_name).vsrcs.f
$(synth_list_f): $(sim_common_files) $(all_vsrcs)
	$(foreach file,$(all_vsrcs),echo "$(file)" >> $@;)
	cat $(sim_common_files) >> $@

BIT_FILE := $(build_dir)/obj/$(MODEL).bit
$(BIT_FILE): $(synth_list_f)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(fpga_common_script_dir)/vivado.tcl \
		-tclargs \
			-top-module "$(MODEL)" \
			-F "$(synth_list_f)" \
			-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')" \
			-board "$(BOARD)"

.PHONY: bitstream
bitstream: $(BIT_FILE)

.PHONY: debug-bitstream
debug-bitstream: $(build_dir)/obj/post_synth.dcp
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source $(sim_dir)/scripts/run_impl_bitstream.tcl \
		-tclargs \
			$(build_dir)/obj/post_synth.dcp \
			$(BOARD) \
			$(build_dir)/debug_obj \
			$(fpga_common_script_dir)

#########################################################################################
# sim rules
#########################################################################################

project-creation-script := $(sim_dir)/scripts/create_project.tcl

# generate the vivado project and add all sources
vivado-project := $(build_dir)/$(MODEL).xpr
$(vivado-project): $(synth_list_f) $(project-creation-script)
	cd $(build_dir); vivado \
		-nojournal -mode batch \
		-source  $(project-creation-script) \
		-tclargs \
			-top-module "$(MODEL)" \
			-F "$(synth_list_f)" \
			-ip-vivado-tcls "$(shell find '$(build_dir)' -name '*.vivado.tcl')" \
			-board "$(BOARD)"

# generate vcs collateral
vivado-vcs-collateral := $(build_dir)/$(MODEL).cache/compile_simlib/synopsys_sim.setup
$(vivado-vcs-collateral): $(sim_dir)/scripts/generate_vcs_collateral.tcl $(vivado-project)
	cd $(build_dir); vivado $(vivado-project) \
		-nojournal -mode batch \
		-source $(sim_dir)/scripts/generate_vcs_collateral.tcl \
		-tclargs \
			$(FPGA_FAMILY) \
			$(build_dir) \
			$(MODEL)

include $(base_dir)/vcs.mk

sim_prefix = simv
sim = $(build_dir)/$(sim_prefix)-$(MODEL_PACKAGE)-$(CONFIG)

fpga_sim_verilog_sources = $(build_dir)/fpga_sim_verilog_sources.f
fpga_sim_vhdl_sources = $(build_dir)/fpga_sim_vhdl_sources.f
fpga_sim_cc_sources = $(build_dir)/fpga_sim_cc_sources.f

xilinx_lib = xil_defaultlib

VCS = vcs -full64
VLOGAN = vlogan -full64
VHDLAN = vhdlan -full64

# todo: deduplication between this and other VCS sim makefile
VLOGAN_OPTS = \
	-notice \
	-line \
	+lint=all,noVCDE,noONGS,noUI \
	-error=PCWM-L \
	-error=noZMMCM \
	-timescale=1ns/10ps \
	-quiet \
	-q \
	+rad \
	+vcs+lic+wait \
	+vc+list \
	-sverilog +systemverilogext+.sv+.svi+.svh+.svt -assert svaext +libext+.sv \
	+v2k +verilog2001ext+.v95+.vt+.vp +libext+.v \
	-debug_pp \
	+incdir+$(build_dir) \
	$(PREPROC_DEFINES)

# vcs-mx flow
$(sim): $(vivado-vcs-collateral)
	mkdir -p $(build_dir)/vcs_lib/xil_defaultlib
	cd $(build_dir); $(VHDLAN) -work $(xilinx_lib) -f $(fpga_sim_vhdl_sources) -l vhdlan.log
	cd $(build_dir); $(VLOGAN) $(VLOGAN_OPTS) -work $(xilinx_lib) -f $(fpga_sim_verilog_sources) -l vlogan.log
	cd $(build_dir); $(VCS) $(VCS_CC_OPTS) $(xilinx_lib).$(TB) $(xilinx_lib).glbl -file $(fpga_sim_cc_sources) -o $@ -Mdir=$(build_dir)/$(long_name) -l vcs.log

.PHONY: sim-fpga
sim-fpga: $(sim)

#########################################################################################
# general cleanup rules
#########################################################################################
.PHONY: clean
clean:
	rm -rf $(gen_dir)
