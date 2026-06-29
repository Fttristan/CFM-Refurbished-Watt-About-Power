package dev.tr3ymix.cfm_wap.forge;

import dev.tr3ymix.cfm_wap.CFM_WAP;
import dev.tr3ymix.cfm_wap.block.CircuitBreakerBlock;
import dev.tr3ymix.cfm_wap.blockentity.CircuitBreakerBlockEntity;
import dev.tr3ymix.cfm_wap.energy.forge.EnergyHandlerFactoryImpl;
import dev.tr3ymix.cfm_wap.util.Utils;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Mod.EventBusSubscriber(modid = CFM_WAP.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ForgeEvents {

    private ForgeEvents() {
    }

    static void onAttachCapabilities(AttachCapabilitiesEvent<BlockEntity> event) {
        BlockEntity entity = event.getObject();
        if(entity instanceof CircuitBreakerBlockEntity breaker){
            event.addCapability(Utils.resource("energy"), new EnergyStorageProvider(breaker));
        }

    }

    private static class EnergyStorageProvider implements net.minecraftforge.common.capabilities.ICapabilityProvider {

        private final LazyOptional<IEnergyStorage> lazyEnergyHandler;
        private final BlockEntity blockEntity;

        public EnergyStorageProvider(CircuitBreakerBlockEntity blockEntity) {
            this.lazyEnergyHandler = LazyOptional.of(() ->
                (IEnergyStorage) EnergyHandlerFactoryImpl.from(blockEntity.ENERGY_STORAGE, blockEntity)
            );
            this.blockEntity = blockEntity;
        }

        @Override
        public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction arg) {
            if(capability == ForgeCapabilities.ENERGY){
                BlockState state = this.blockEntity.getBlockState();
                if(state.hasProperty(CircuitBreakerBlock.DIRECTION)){
                    if(state.getValue(CircuitBreakerBlock.DIRECTION) == arg){
                        return lazyEnergyHandler.cast();
                    }
                }
            }
            return LazyOptional.empty();
        }
    }
}
