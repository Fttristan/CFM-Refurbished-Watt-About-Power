package dev.tr3ymix.cfm_wap.energy.forge;

import dev.tr3ymix.cfm_wap.energy.CommonEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler;

@SuppressWarnings("unused")
public class EnergyHandlerFactoryImpl {

    /**
     * Standard Forge Energy handler
     */
    public static Object from(CommonEnergyStorage storage) {
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int i, boolean b) {
                return storage.insertEnergy(i, b);
            }

            @Override
            public int extractEnergy(int i, boolean b) {
                return storage.extractEnergy(i, b);
            }

            @Override
            public int getEnergyStored() {
                return storage.getEnergy();
            }

            @Override
            public int getMaxEnergyStored() {
                return storage.getCapacity();
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    /**
     * Powergrid Bridge handler
     * This links the Powergrid simulation to your CommonEnergyStorage
     */
    public static IFEBridgeHandler bridgeFrom(CommonEnergyStorage storage, BlockEntity be) {
        return new IFEBridgeHandler() {
            @Override
            public long getAmount() {
                return storage.getEnergy();
            }

            @Override
            public void setAmount(long amount) {
                // Since FE typically uses 'int', we clamp the long to stay within bounds
                int target = (int) Math.min(amount, Integer.MAX_VALUE);
                int current = storage.getEnergy();
                
                if (target > current) {
                    storage.insertEnergy(target - current, false);
                } else if (target < current) {
                    storage.extractEnergy(current - target, false);
                }
            }

            @Override
            public long moveEnergy() {
                // This logic attempts to push energy from your storage into adjacent blocks
                if (be.getLevel() == null || be.getLevel().isClientSide) return 0;

                int totalMoved = 0;
                int amountToMove = storage.getEnergy();
                if (amountToMove <= 0) return 0;

                for (Direction dir : Direction.values()) {
                    BlockEntity targetBe = be.getLevel().getBlockEntity(be.getBlockPos().relative(dir));
                    if (targetBe != null) {
                        // Check if the neighbor accepts Forge Energy
                        targetBe.getCapability(ForgeCapabilities.ENERGY, dir.getOpposite()).ifPresent(otherStorage -> {
                            if (otherStorage.canReceive()) {
                                // Extract from our storage, insert into theirs
                                int taken = storage.extractEnergy(amountToMove, true);
                                int accepted = otherStorage.receiveEnergy(taken, false);
                                storage.extractEnergy(accepted, false);
                            }
                        });
                    }
                }
                // We return 0 or the delta here to let the simulation know the flow rate
                // The BridgeElectricBehaviour uses this for client-side syncing
                return totalMoved;
            }

            @Override
            public void setChanged() {
                be.setChanged();
            }
        };
    }
}
