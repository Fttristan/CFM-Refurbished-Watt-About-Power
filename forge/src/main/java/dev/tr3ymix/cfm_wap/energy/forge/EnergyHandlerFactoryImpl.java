package dev.tr3ymix.cfm_wap.energy.forge;

import dev.tr3ymix.cfm_wap.energy.CommonEnergyStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class EnergyHandlerFactoryImpl {

    private static final String BRIDGE_HANDLER_CLASS = "org.patryk3211.powergrid.electricity.febridge.IFEBridgeHandler";
    private static volatile Class<?> cachedBridgeHandler;
    private static volatile boolean bridgeLookupDone;

    public static Object from(CommonEnergyStorage storage) {
        return from(storage, null);
    }

    public static Object from(CommonEnergyStorage storage, @Nullable BlockEntity blockEntity) {
        List<Class<?>> interfaces = new ArrayList<>();
        interfaces.add(IEnergyStorage.class);

        Class<?> bridgeInterface = getBridgeHandlerInterface();
        if(bridgeInterface != null && bridgeInterface.isInterface()) {
            interfaces.add(bridgeInterface);
        }

        InvocationHandler handler = new BridgeEnergyHandler(storage, blockEntity);
        return Proxy.newProxyInstance(
            EnergyHandlerFactoryImpl.class.getClassLoader(),
            interfaces.toArray(new Class<?>[0]),
            handler
        );
    }

    private static @Nullable Class<?> getBridgeHandlerInterface() {
        if(bridgeLookupDone) {
            return cachedBridgeHandler;
        }
        synchronized (EnergyHandlerFactoryImpl.class) {
            if(bridgeLookupDone) {
                return cachedBridgeHandler;
            }
            bridgeLookupDone = true;
            try {
                cachedBridgeHandler = Class.forName(BRIDGE_HANDLER_CLASS);
            } catch (ClassNotFoundException ignored) {
                cachedBridgeHandler = null;
            }
            return cachedBridgeHandler;
        }
    }

    private static final class BridgeEnergyHandler implements InvocationHandler {

        private final CommonEnergyStorage storage;
        private final @Nullable BlockEntity blockEntity;

        private BridgeEnergyHandler(CommonEnergyStorage storage, @Nullable BlockEntity blockEntity) {
            this.storage = storage;
            this.blockEntity = blockEntity;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.getName();
            Class<?> returnType = method.getReturnType();

            if(name.equals("toString")) {
                return "BridgeEnergyHandler[" + this.storage.getEnergy() + "/" + this.storage.getCapacity() + "]";
            }
            if(name.equals("hashCode")) {
                return System.identityHashCode(proxy);
            }
            if(name.equals("equals")) {
                return args != null && args.length > 0 && proxy == args[0];
            }

            if(name.equals("receiveEnergy") || name.equals("insert")) {
                int amount = intArg(args, 0, 0);
                boolean simulate = boolArg(args, 1, false);
                return convertNumber(this.storage.insertEnergy(amount, simulate), returnType);
            }

            if(name.equals("extractEnergy") || name.equals("extract")) {
                int amount = intArg(args, 0, 0);
                boolean simulate = boolArg(args, 1, false);
                return convertNumber(this.storage.extractEnergy(amount, simulate), returnType);
            }

            if(name.equals("getEnergyStored") || name.equals("getAmount") || name.equals("getEnergy")) {
                return convertNumber(this.storage.getEnergy(), returnType);
            }

            if(name.equals("getMaxEnergyStored") || name.equals("getMaxAmount") || name.equals("getCapacity")) {
                return convertNumber(this.storage.getCapacity(), returnType);
            }

            if(name.equals("setAmount") || name.equals("setEnergy")) {
                int target = intArg(args, 0, this.storage.getEnergy());
                int current = this.storage.getEnergy();
                if(target > current) {
                    this.storage.insertEnergy(target - current, false);
                } else if(target < current) {
                    this.storage.extractEnergy(current - target, false);
                }
                return defaultValue(returnType);
            }

            if(name.equals("moveEnergy")) {
                return convertNumber(this.moveEnergy(), returnType);
            }

            if(name.equals("setChanged")) {
                if(this.blockEntity != null) {
                    this.blockEntity.setChanged();
                }
                return defaultValue(returnType);
            }

            if(name.equals("canReceive") || name.equals("canExtract")) {
                return true;
            }

            return defaultValue(returnType);
        }

        private long moveEnergy() {
            if(this.blockEntity == null || this.blockEntity.getLevel() == null || this.blockEntity.getLevel().isClientSide) {
                return 0;
            }

            int totalMoved = 0;
            if(this.storage.getEnergy() <= 0) {
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

        private static int intArg(Object[] args, int index, int fallback) {
            if(args == null || index < 0 || index >= args.length || !(args[index] instanceof Number number)) {
                return fallback;
            }
            return number.intValue();
        }

        private static boolean boolArg(Object[] args, int index, boolean fallback) {
            if(args == null || index < 0 || index >= args.length || !(args[index] instanceof Boolean bool)) {
                return fallback;
            }
            return bool;
        }

        private static Object convertNumber(long value, Class<?> returnType) {
            if(returnType == long.class || returnType == Long.class) {
                return value;
            }
            if(returnType == int.class || returnType == Integer.class) {
                return (int) value;
            }
            if(returnType == short.class || returnType == Short.class) {
                return (short) value;
            }
            if(returnType == byte.class || returnType == Byte.class) {
                return (byte) value;
            }
            if(returnType == float.class || returnType == Float.class) {
                return (float) value;
            }
            if(returnType == double.class || returnType == Double.class) {
                return (double) value;
            }
            return value;
        }

        private static Object defaultValue(Class<?> returnType) {
            if(returnType == void.class) {
                return null;
            }
            if(returnType == boolean.class || returnType == Boolean.class) {
                return false;
            }
            if(returnType == byte.class || returnType == Byte.class) {
                return (byte) 0;
            }
            if(returnType == short.class || returnType == Short.class) {
                return (short) 0;
            }
            if(returnType == int.class || returnType == Integer.class) {
                return 0;
            }
            if(returnType == long.class || returnType == Long.class) {
                return 0L;
            }
            if(returnType == float.class || returnType == Float.class) {
                return 0F;
            }
            if(returnType == double.class || returnType == Double.class) {
                return 0D;
            }
            if(returnType == char.class || returnType == Character.class) {
                return '\0';
            }
            return null;
        }
    }
}