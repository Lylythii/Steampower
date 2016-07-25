package flaxbeard.steamcraft.tile;

import flaxbeard.steamcraft.Config;
import flaxbeard.steamcraft.api.steamnet.SteamNetwork;
import flaxbeard.steamcraft.api.steamnet.SteamNetworkRegistry;
import flaxbeard.steamcraft.block.BlockValvePipe;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.ArrayList;

public class TileEntityValvePipe extends TileEntitySteamPipe {
    public boolean open = true;
    public int turnTicks = 0;
    private boolean turning;
    private boolean wasTurning = false;
    private boolean redstoneState;
    private boolean waitingOpen = false;

    public TileEntityValvePipe() {
        super(0);
    }

    /**
     * Updates the valve's redstone state, and opens/closes it accordingly.
     * @param flag True to isOpen it, false to close it.
     */
    public void updateRedstoneState(boolean flag) {
		if (Config.enableRedstoneValvePipe) {
			if (!isTurning()) {
                setOpen(flag);
            }
		}
        redstoneState = flag;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound access = super.getDescriptionTag();

        access.setBoolean("turning", turning);
        access.setBoolean("isOpen", open);
        access.setBoolean("leaking", isLeaking);
        access.setInteger("turnTicks", turnTicks);

        return new SPacketUpdateTileEntity(pos, 1, access);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        NBTTagCompound access = pkt.getNbtCompound();
        if (turnTicks == 0) {
            turnTicks = access.getInteger("turnTicks");
        }
        turning = access.getBoolean("turning");
        isLeaking = access.getBoolean("leaking");
        open = access.getBoolean("isOpen");
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        open = nbt.getBoolean("isOpen");
        redstoneState = nbt.getBoolean("redstoneState");
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("isOpen", open);
        nbt.setBoolean("redstoneState", redstoneState);
        return nbt;
    }

    public EnumFacing dir() {
        return worldObj.getBlockState(pos).getValue(BlockValvePipe.FACING);
    }

    @Override
    public boolean doesConnect(EnumFacing face) {
        return face != dir() && super.doesConnect(face);
    }

    @Override
    public ArrayList<EnumFacing> getMyDirections() {
        return super.getMyDirections();
    }

    @Override
    public boolean canLeak(EnumFacing direction) {
        SteamNetwork net = getNetwork();
        if (net == null) {
            return false;
        }
        BlockPos dirPos = getOffsetPos(direction);
        /*
         No super call, because the valve pipe does not actually get a share of the network. For the valve pipe,
         we have to actually check the amount of steam in the network that it is connected to.
          */
        return isOpen() && net.getSteam() > 0 && (worldObj.isAirBlock(dirPos) ||
          !worldObj.isSideSolid(dirPos, direction.getOpposite()));
    }

    @Override
    public void update() {
        super.superUpdate();
        if (worldObj.isRemote) {
            if (turning && turnTicks < 10) {
                turnTicks++;
            }
            if (turnTicks >= 10) {
                turning = false;
                setOpen(!open);
                turnTicks = 0;
            }
            if (!turning) {
                turnTicks = 0;
            }
        } else {
            if (waitingOpen) {
                //Steamcraft.log.debug("Waiting for isOpen");
                setOpen(!open);
            }
            if (turning != wasTurning) {
                wasTurning = turning;
                markForUpdate();
            }
            if (turning && turnTicks < 10) {
                turnTicks++;
            }
            if (turnTicks >= 10) {
                turning = false;
                setOpen(!open);
                turnTicks = 0;
            }
            if (!turning) {
                if (wasTurning) {
                    markForUpdate();
                }
                turnTicks = 0;
            }
        }
        leak();
    }

    @Override
    public boolean acceptsGauge(EnumFacing face) {
        return face != dir().getOpposite();
    }

    public boolean isTurning() {
        return turning;
    }

    public void setTurning() {
        turning = true;
        turnTicks = 0;
    }

    public boolean isOpen() {
        return open;
    }

    private void setOpen(boolean open) {
        this.open = open;
        boolean changed = true;
        if (!worldObj.isRemote) {
            if (open) {
                //Steamcraft.log.debug("Joining");
                if (SteamNetworkRegistry.getInstance().isInitialized(getDimension())) {
                    SteamNetwork.newOrJoin(this);
                } else {
                    changed = false;
                    waitingOpen = true;
                }
            } else {
                //Steamcraft.log.debug("Splitting");
                if (getNetwork() != null) {
                    getNetwork().split(this, true);
                } else {
                    changed = false;
                    waitingOpen = true;
                }
            }
        }
        if (!changed) {
            this.open = !open;
        } else {
            waitingOpen = false;
            markForUpdate();
        }
    }

    @Override
    public boolean onWrench(ItemStack stack, EntityPlayer player, World world, BlockPos pos, EnumHand hand, EnumFacing facing, IBlockState state, float hitX, float hitY, float hitZ) {
        return false;
    }
}
