package gregtech.api.capability.impl;

import gregtech.api.capability.IFilter;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

/**
 * Basic filter with one fluid template. Can be either whitelist or blacklist.
 */
public final class SingleFluidFilter implements IFilter<FluidStack> {

    private final FluidStack fluid;
    private final boolean blacklist;

    public SingleFluidFilter(@Nonnull FluidStack fluid, boolean blacklist) {
        this.fluid = fluid;
        this.blacklist = blacklist;
    }

    @Nonnull
    public FluidStack getFluid() {
        return fluid;
    }

    public boolean isWhitelist() {
        return !blacklist;
    }

    public boolean isBlacklist() {
        return blacklist;
    }

    @Override
    public boolean test(@Nonnull FluidStack fluid) {
        return this.fluid.isFluidEqual(fluid) != this.blacklist;
    }

    @Override
    public int getPriority() {
        return this.blacklist ? IFilter.blacklistPriority(1) : IFilter.whitelistPriority(1);
    }

    @Override
    public IFilter<FluidStack> negate() {
        return new SingleFluidFilter(this.fluid, !this.blacklist);
    }

    @Override
    public String toString() {
        return "SingleFluidFilter{" +
                "fluid=" + fluid +
                ", blacklist=" + blacklist +
                '}';
    }
}
