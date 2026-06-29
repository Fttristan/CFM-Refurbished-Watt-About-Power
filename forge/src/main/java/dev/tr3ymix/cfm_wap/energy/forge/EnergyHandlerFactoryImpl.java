package dev.tr3ymix.cfm_wap.energy.forge;

import dev.tr3ymix.cfm_wap.energy.CommonEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("unused")
public class EnergyHandlerFactoryImpl {

    public static Object from(CommonEnergyStorage storage) {
        // Return a dual-interface handler so both Forge FE and PowerGrid bridge code can consume it.
        return new BridgeEnergyHandler(storage, null);
    }

    public static IFEBridgeHandler bridgeFrom(CommonEnergyStorage storage, BlockEntity be) {
        return new BridgeEnergyHandler(storage, be);
    }

    private static final class BridgeEnergyHandler implements IEnergyStorage, IFEBridgeHandler {

        private final CommonEnergyStorage storage;
        private final @Nullable BlockEntity blockEntity;

        private BridgeEnergyHandler(CommonEnergyStorage storage, @Nullable BlockEntity blockEntity) {
            this.storage = storage;
            this.blockEntity = blockEntity;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            return this.storage.insertEnergy(amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return this.storage.extractEnergy(amount, simulate);
        }

        @Override
        public int getEnergyStored() {
            return this.storage.getEnergy();
        }

        @Override
        public int getMaxEnergyStored() {
            return this.storage.getCapacity();
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public long getAmount() {
            return this.storage.getEnergy();
        }

        @Override
        public void setAmount(long amount) {
            int target = (int) Math.min(amount, Integer.MAX_VALUE);
            int current = this.storage.getEnergy();

            if(target > current) {
                this.storage.insertEnergy(target - current, false);
            } else if(target < current) {
                this.storage.extractEnergy(current - target, false);
            }
        }

        @Override
        public long moveEnergy() {
            if(this.blockEntity == null || this.blockEntity.getLevel() == null || this.blockEntity.getLevel().isClientSide) {
                return 0;
            }

            int totalMoved = 0;
            int available = this.storage.getEnergy();
            if(available <= 0) {
                return 0;
            }

            for(Direction direction : Direction.values()) {
                BlockEntity target = this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos().relative(direction));
                if(target == null) {
                    continue;
                }

                int amountForSide = this.storage.getEnergy();
                if(amountForSide <= 0) {
                    break;
                }

                int moved = target.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite())
                    .map(otherStorage -> {
                        if(!otherStorage.canReceive()) {
                            return 0;
                        }
                        int simulated = this.storage.extractEnergy(amountForSide, true);
                        if(simulated <= 0) {
                            return 0;
                        }
                        int accepted = otherStorage.receiveEnergy(simulated, false);
                        if(accepted > 0) {
                            this.storage.extractEnergy(accepted, false);
                        }
                        return accepted;
                    })
                    .orElse(0);

                totalMoved += moved;
            }
            return totalMoved;
        }

        @Override
        public void setChanged() {
            if(this.blockEntity != null) {
                this.blockEntity.setChanged();
            }
        }
    }
}