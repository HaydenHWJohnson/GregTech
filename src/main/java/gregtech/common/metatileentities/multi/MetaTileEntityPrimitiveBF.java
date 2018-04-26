package gregtech.common.metatileentities.multi;

import codechicken.lib.render.CCRenderState;
import codechicken.lib.render.pipeline.IVertexOperation;
import gregtech.api.gui.GuiTextures;
import gregtech.api.gui.ModularUI;
import gregtech.api.gui.widgets.ProgressWidget.MoveType;
import gregtech.api.gui.widgets.SlotWidget;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntityHolder;
import gregtech.api.metatileentity.multiblock.MultiblockControllerBase;
import gregtech.api.multiblock.BlockPattern;
import gregtech.api.multiblock.FactoryBlockPattern;
import gregtech.api.recipes.Recipe.PrimitiveBlastFurnaceRecipe;
import gregtech.api.recipes.RecipeMaps;
import gregtech.api.render.SimpleSidedRenderer;
import gregtech.api.render.Textures;
import gregtech.api.unification.OreDictUnifier;
import gregtech.api.unification.material.Materials;
import gregtech.api.unification.ore.OrePrefix;
import gregtech.api.unification.stack.SimpleItemStack;
import gregtech.common.blocks.BlockMetalCasing.MetalCasingType;
import gregtech.common.blocks.MetaBlocks;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.Vec3i;
import net.minecraftforge.common.util.Constants.NBT;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;

public class MetaTileEntityPrimitiveBF extends MultiblockControllerBase {

    private int maxProgressDuration;
    private int currentProgress;
    private NonNullList<ItemStack> outputsList;
    private boolean isActive;
    private boolean wasActiveAndNeedUpdate;

    public MetaTileEntityPrimitiveBF(String metaTileEntityId) {
        super(metaTileEntityId);
    }

    @Override
    protected void updateFormedValid() {
        if(maxProgressDuration == 0) {
            if(tryPickNewRecipe()) {
                if(wasActiveAndNeedUpdate) {
                    this.wasActiveAndNeedUpdate = false;
                } else setActive(true);
            }
        } else if(++currentProgress >= maxProgressDuration) {
            finishCurrentRecipe();
            this.wasActiveAndNeedUpdate = true;
            return;
        }
        if(wasActiveAndNeedUpdate) {
            this.wasActiveAndNeedUpdate = false;
            setActive(false);
        }
    }

    private void finishCurrentRecipe() {
        this.maxProgressDuration = 0;
        this.currentProgress = 0;
        MetaTileEntity.addItemsToItemHandler(exportItems, false, outputsList);
        this.outputsList = null;
        markDirty();
    }

    private boolean tryPickNewRecipe() {
        ItemStack inputStack = importItems.getStackInSlot(0);
        ItemStack fuelStack = importItems.getStackInSlot(1);
        if(inputStack.isEmpty() || fuelStack.isEmpty()) return false;
        int fuelUnitsPerItem = getFuelUnits(fuelStack);
        int fuelAmount = fuelUnitsPerItem * fuelStack.getCount();
        if(inputStack.isEmpty() || fuelAmount == 0) return false;
        SimpleItemStack simpleInput = new SimpleItemStack(inputStack);
        PrimitiveBlastFurnaceRecipe recipe = RecipeMaps.PRIMITIVE_BLAST_FURNACE_RECIPES.stream()
            .filter(recipe1 -> recipe1.getInput().apply(inputStack))
            .findFirst().orElse(null);
        if(recipe == null || fuelAmount < recipe.getFuelAmount()) return false;
        NonNullList<ItemStack> outputs = NonNullList.create();
        outputs.add(recipe.getOutput());
        outputs.add(OreDictUnifier.get(OrePrefix.dustTiny, Materials.DarkAsh, Math.abs(3 - fuelUnitsPerItem)));
        if(MetaTileEntity.addItemsToItemHandler(exportItems, true, outputs)) {
            fuelStack.shrink(Math.max(1, recipe.getFuelAmount() / fuelUnitsPerItem));
            inputStack.shrink(1);
            importItems.setStackInSlot(1, fuelStack);
            importItems.setStackInSlot(0, inputStack);
            this.maxProgressDuration = recipe.getDuration();
            this.currentProgress = 0;
            this.outputsList = outputs;
            markDirty();
            return true;
        }
        return false;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        data.setBoolean("Active", isActive);
        data.setBoolean("WasActive", wasActiveAndNeedUpdate);
        data.setInteger("MaxProgress", maxProgressDuration);
        if(maxProgressDuration > 0) {
            data.setInteger("Progress", currentProgress);
            NBTTagList itemOutputs = new NBTTagList();
            for(ItemStack itemStack : outputsList) {
                itemOutputs.appendTag(itemStack.writeToNBT(new NBTTagCompound()));
            }
            data.setTag("Outputs", itemOutputs);
        }
        return data;
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        this.isActive = data.getBoolean("Active");
        this.wasActiveAndNeedUpdate = data.getBoolean("WasActive");
        this.maxProgressDuration = data.getInteger("MaxProgress");
        if(maxProgressDuration > 0) {
            this.currentProgress = data.getInteger("Progress");
            NBTTagList itemOutputs = data.getTagList("Outputs", NBT.TAG_COMPOUND);
            this.outputsList = NonNullList.create();
            for(int i = 0; i < itemOutputs.tagCount(); i++) {
                this.outputsList.add(new ItemStack(itemOutputs.getCompoundTagAt(i)));
            }
        }
    }

    @Override
    public void writeInitialSyncData(PacketBuffer buf) {
        super.writeInitialSyncData(buf);
        buf.writeBoolean(isActive);
    }

    @Override
    public void receiveInitialSyncData(PacketBuffer buf) {
        super.receiveInitialSyncData(buf);
        this.isActive = buf.readBoolean();
    }

    @Override
    public void receiveCustomData(int dataId, PacketBuffer buf) {
        super.receiveCustomData(dataId, buf);
        if(dataId == -100) {
            this.isActive = buf.readBoolean();
        }
    }

    public void setActive(boolean active) {
        this.isActive = active;
        if(!getWorld().isRemote) {
            writeCustomData(-100, b -> b.writeBoolean(isActive));
        }
    }

    public boolean isActive() {
        return isActive;
    }

    public double getProgressScaled() {
        return maxProgressDuration == 0 ? 0.0 : (currentProgress / (maxProgressDuration * 1.0));
    }

    protected int getFuelUnits(ItemStack fuelType) {
        if(fuelType.getItem() == Items.COAL)
            return 1;
        else if(OreDictUnifier.getOreDictionaryNames(fuelType).contains("fuelCoke"))
            return 2; //assign 2 fuel units to all fuels with fuelCoke tag
        return 0;
    }

    protected IBlockState getCasingState() {
        return MetaBlocks.METAL_CASING.getState(MetalCasingType.BRONZE_BRICKS);
    }

    @Override
    public SimpleSidedRenderer getBaseTexture() {
        return Textures.STEAM_BRONZE_BRICK_CASING;
    }

    @Override
    public void renderMetaTileEntity(CCRenderState renderState, IVertexOperation[] pipeline) {
        super.renderMetaTileEntity(renderState, pipeline);
        Textures.PRIMITIVE_BLAST_FURNACE_OVERLAY.render(renderState, pipeline, getFrontFacing(), isActive());
    }

    @Override
    protected IItemHandlerModifiable createImportItemHandler() {
        return new ItemStackHandler(2) {
            @Nonnull
            @Override
            public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
                if(slot == 1 && getFuelUnits(stack) == 0)
                    return stack;
                return super.insertItem(slot, stack, simulate);
            }
        };
    }

    @Override
    protected IItemHandlerModifiable createExportItemHandler() {
        return new ItemStackHandler(2);
    }

    @Override
    protected Vec3i getCenterOffset() {
        return new Vec3i(1, -1, 0);
    }

    @Override
    protected BlockPattern createStructurePattern() {
        return FactoryBlockPattern.start()
            .aisle("XXX", "XXX", "XXX", "XXX")
            .aisle("XXX", "X#X", "X#X", "X#X")
            .aisle("XXX", "XYX", "XXX", "XXX")
            .where('X', statePredicate(getCasingState()))
            .where('#', blockPredicate(Blocks.AIR))
            .where('Y', selfPredicate())
            .build();
    }

    @Override
    public MetaTileEntity createMetaTileEntity(MetaTileEntityHolder holder) {
        return new MetaTileEntityPrimitiveBF(metaTileEntityId);
    }

    @Override
    protected ModularUI createUI(EntityPlayer entityPlayer) {
        return ModularUI.builder(GuiTextures.BRONZE_BACKGROUND, 176, 166)
            .image(11, 12, 17, 50, GuiTextures.PATTERN_BRONZE_BLAST_FURNACE)
            .widget(new SlotWidget(importItems, 0, 33, 15, true, true)
                .setBackgroundTexture(GuiTextures.BRONZE_SLOT, GuiTextures.BRONZE_INGOT_OVERLAY))
            .widget(new SlotWidget(importItems, 1, 33, 33, true, true)
                .setBackgroundTexture(GuiTextures.BRONZE_SLOT, GuiTextures.BRONZE_FURNACE_OVERLAY))
            .progressBar(this::getProgressScaled, 58, 24, 20, 15, GuiTextures.BRONZE_BLAST_FURNACE_PROGRESS_BAR, MoveType.HORIZONTAL)
            .widget(new SlotWidget(exportItems, 0, 85, 24, true, false)
                .setBackgroundTexture(GuiTextures.BRONZE_SLOT, GuiTextures.BRONZE_INGOT_OVERLAY))
            .widget(new SlotWidget(exportItems, 1, 103, 24, true, false)
                .setBackgroundTexture(GuiTextures.BRONZE_SLOT, GuiTextures.BRONZE_DUST_OVERLAY))
            .bindPlayerInventory(entityPlayer.inventory, GuiTextures.BRONZE_SLOT)
            .build(getHolder(), entityPlayer);
    }
}
