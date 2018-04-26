package gregtech.api.metatileentity.multiblock;

import gregtech.api.capability.IEnergyContainer;
import gregtech.api.capability.impl.EnergyContainerList;
import gregtech.api.capability.impl.FluidTankList;
import gregtech.api.capability.impl.ItemHandlerList;
import gregtech.api.capability.impl.MultiblockRecipeMapWorkable;
import gregtech.api.multiblock.PatternMatchContext;
import gregtech.api.recipes.Recipe;
import gregtech.api.recipes.RecipeMap;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraftforge.items.IItemHandler;

import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class RecipeMapMultiblockController extends MultiblockWithDisplayBase {

    public final RecipeMap<?> recipeMap;
    private MultiblockRecipeMapWorkable recipeMapWorkable;

    public RecipeMapMultiblockController(String metaTileEntityId, RecipeMap<?> recipeMap) {
        super(metaTileEntityId);
        this.recipeMap = recipeMap;
        this.recipeMapWorkable = new MultiblockRecipeMapWorkable(this, recipeMap, this::checkRecipe);
    }

    protected boolean shouldUseEnergyOutputs() {
        return false;
    }

    /**
     * Performs extra checks for validity of given recipe before multiblock
     * will start it's processing.
     */
    protected boolean checkRecipe(Recipe recipe, boolean consumeIfSuccess) {
        return true;
    }

    @Override
    protected void formStructure(PatternMatchContext context) {
        super.formStructure(context);
        ItemHandlerList importItemsList = new ItemHandlerList(getAbilities(MultiblockAbility.IMPORT_ITEMS));
        FluidTankList importFluidsList = new FluidTankList(getAbilities(MultiblockAbility.IMPORT_FLUIDS));
        ItemHandlerList exportItemsList = new ItemHandlerList(getAbilities(MultiblockAbility.EXPORT_ITEMS));
        FluidTankList exportFluidsList = new FluidTankList(getAbilities(MultiblockAbility.EXPORT_FLUIDS));
        EnergyContainerList energyContainerList = new EnergyContainerList(getAbilities(shouldUseEnergyOutputs() ? MultiblockAbility.OUTPUT_ENERGY : MultiblockAbility.INPUT_ENERGY));
        this.recipeMapWorkable.reinitializeAbilities(importItemsList, importFluidsList, exportItemsList, exportFluidsList, energyContainerList);
    }

    @Override
    public void invalidateStructure() {
        super.invalidateStructure();
        this.recipeMapWorkable.resetAbilities();
    }

    @Override
    protected void updateFormedValid() {
        this.recipeMapWorkable.updateWorkable();
    }

    @Override
    protected void addDisplayText(List<ITextComponent> textList) {
        super.addDisplayText(textList);
        if(!recipeMapWorkable.isWorkingEnabled()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.work_paused"));
        } else if(recipeMapWorkable.isActive()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.running"));
            double currentProgress = recipeMapWorkable.getProgressPercent() * 100;
            textList.add(new TextComponentTranslation("gregtech.multiblock.progress", currentProgress));
        } else {
            textList.add(new TextComponentTranslation("gregtech.multiblock.idling"));
        }
        if(recipeMapWorkable.isHasNotEnoughEnergy()) {
            textList.add(new TextComponentTranslation("gregtech.multiblock.not_enough_energy"));
        }
    }

    @Override
    protected BooleanSupplier getValidationPredicate() {
        return () -> {
            //basically check minimal requirements for inputs count & amperage
            int itemInputsCount = getAbilities(MultiblockAbility.IMPORT_ITEMS)
                .stream().mapToInt(IItemHandler::getSlots).sum();
            int fluidInputsCount = getAbilities(MultiblockAbility.IMPORT_FLUIDS).size();
            long maxAmperage = getAbilities(MultiblockAbility.INPUT_ENERGY).stream()
                .mapToLong(IEnergyContainer::getInputAmperage).sum();
            return itemInputsCount >= recipeMap.getMinInputs() &&
                fluidInputsCount >= recipeMap.getMinFluidInputs() &&
                maxAmperage >= recipeMap.getAmperage();
        };
    }


}
