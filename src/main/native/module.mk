include $(TOOLCHAIN_DIR)/arch/$(ARCH).mk

CXXFLAGS        :=  $(CXXFLAGS) -DFP2_MODULE=$(subst /,_,$(MODULE)) $(addprefix -I,$(INCLUDES) $(CURDIR))

ifdef SIMD_EXT
include $(TOOLCHAIN_DIR)/simd/$(BASE_ARCH)/$(SIMD_EXT).mk

OFILE_SUFFIX	:=	o.$(SIMD_EXT)
OUTPUT_FILE     :=  $(OUTPUT_DIR)/$(MODULE)/$(ARCH)/$(SIMD_EXT).$(EXT)
CXXFLAGS		:=	$(CXXFLAGS) -D_FP2_VEC_SIZE=$(SIMD_REGISTER_WIDTH)
else
OFILE_SUFFIX	:=	o
OUTPUT_FILE     :=  $(OUTPUT_DIR)/$(MODULE)/$(ARCH).$(EXT)
endif

HEADERFILES	    :=	$(HEADERFILES) $(wildcard $(MODULE)/*.h) $(wildcard $(MODULE)/**/*.h)
SRCFILES		:=	$(wildcard $(MODULE)/*.cpp) $(wildcard $(MODULE)/**/*.cpp)
OFILES			:=	$(addprefix $(COMPILE_DIR)/$(ARCH)/,$(addsuffix .$(OFILE_SUFFIX),$(SRCFILES)))

build: $(OUTPUT_FILE)
	@echo "built $(MODULE) for $(ARCH)$(foreach ext,$(SIMD_EXT), with $(ext))"

$(OUTPUT_FILE): $(OFILES)
	@[ -d $(dir $(OUTPUT_FILE)) ] || mkdir -p $(dir $(OUTPUT_FILE))
	@echo "linking $(OUTPUT_FILE)"
	@$(LD) $(LDFLAGS) -o $(OUTPUT_FILE) $(OFILES)
	@echo "stripping $(OUTPUT_FILE)"
	@$(STRIP) $(OUTPUT_FILE)

$(COMPILE_DIR)/$(ARCH)/%.cpp.$(OFILE_SUFFIX): %.cpp $(HEADERFILES)
	@[ -d $(dir $@) ] || mkdir -p $(dir $@)
	@echo "building $@"
	@$(CXX) $(CXXFLAGS) -c $*.cpp -o $@
