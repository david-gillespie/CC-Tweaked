/*
 * This file is part of ComputerCraft - http://www.computercraft.info
 * Copyright Daniel Ratcliffe, 2011-2017. Do not distribute without permission.
 * Send enquiries to dratcliffe@gmail.com
 */

package dan200.computercraft.shared.peripheral.modem;

import com.google.common.base.Objects;
import dan200.computercraft.ComputerCraft;
import dan200.computercraft.api.network.wired.IWiredElement;
import dan200.computercraft.api.network.wired.IWiredNode;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.common.BlockGeneric;
import dan200.computercraft.shared.peripheral.PeripheralType;
import dan200.computercraft.shared.peripheral.common.BlockCable;
import dan200.computercraft.shared.peripheral.common.BlockCableModemVariant;
import dan200.computercraft.shared.peripheral.common.PeripheralItemFactory;
import dan200.computercraft.shared.wired.CapabilityWiredElement;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.NonNullList;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class TileCable extends TileModemBase
{
    public static final double MIN = 0.375;
    public static final double MAX = 1 - MIN;

    private static final AxisAlignedBB BOX_CENTRE = new AxisAlignedBB( MIN, MIN, MIN, MAX, MAX, MAX );
    private static final AxisAlignedBB[] BOXES = new AxisAlignedBB[]{
        new AxisAlignedBB( MIN, 0, MIN, MAX, MIN, MAX ),   // Down
        new AxisAlignedBB( MIN, MAX, MIN, MAX, 1, MAX ),   // Up
        new AxisAlignedBB( MIN, MIN, 0, MAX, MAX, MIN ),   // North
        new AxisAlignedBB( MIN, MIN, MAX, MAX, MAX, 1 ),   // South
        new AxisAlignedBB( 0, MIN, MIN, MIN, MAX, MAX ),   // West
        new AxisAlignedBB( MAX, MIN, MIN, 1, MAX, MAX ),   // East
    };

    private static class CableElement extends WiredModemElement
    {
        private final TileCable m_entity;

        private CableElement( TileCable m_entity )
        {
            this.m_entity = m_entity;
        }

        @Nonnull
        @Override
        public World getWorld()
        {
            return m_entity.getWorld();
        }

        @Nonnull
        @Override
        public Vec3d getPosition()
        {
            BlockPos pos = m_entity.getPos();
            return new Vec3d( pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5 );
        }

        @Override
        protected void attachPeripheral( String name, IPeripheral peripheral )
        {
            ((WiredModemPeripheral) m_entity.m_modem).attachPeripheral( name, peripheral );
        }

        @Override
        protected void detachPeripheral( String name )
        {
            ((WiredModemPeripheral) m_entity.m_modem).detachPeripheral( name );
        }
    }

    // Members

    private boolean m_peripheralAccessAllowed;
    private WiredModemLocalPeripheral m_peripheral = new WiredModemLocalPeripheral();

    private boolean m_destroyed = false;

    private boolean m_hasDirection = false;
    private boolean m_connectionsFormed = false;

    private WiredModemElement m_cable;
    private IWiredNode m_node;

    @Override
    protected ModemPeripheral createPeripheral()
    {
        m_cable = new CableElement( this );
        m_node = m_cable.getNode();
        return new WiredModemPeripheral( m_cable )
        {
            @Nonnull
            @Override
            protected WiredModemLocalPeripheral getLocalPeripheral()
            {
                return m_peripheral;
            }

            @Nonnull
            @Override
            public Vec3d getPosition()
            {
                BlockPos pos = getPos().offset( getCachedDirection() );
                return new Vec3d( pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5 );
            }
        };
    }

    private void remove()
    {
        if( world == null || !world.isRemote )
        {
            m_node.remove();
            m_connectionsFormed = false;
        }
    }

    @Override
    public void destroy()
    {
        if( !m_destroyed )
        {
            m_destroyed = true;
            remove();
        }
        super.destroy();
    }

    @Override
    public void onChunkUnload()
    {
        super.onChunkUnload();
        remove();
    }

    @Override
    public void invalidate()
    {
        super.invalidate();
        remove();
    }

    @Override
    public void onLoad()
    {
        super.onLoad();
        updateDirection();
    }

    @Override
    public void updateContainingBlockInfo()
    {
        m_hasDirection = false;
    }

    private void updateDirection()
    {
        if( !m_hasDirection )
        {
            m_hasDirection = true;
            m_dir = getDirection();
        }
    }

    @Override
    public EnumFacing getDirection()
    {
        IBlockState state = getBlockState();
        BlockCableModemVariant modem = state.getValue( BlockCable.Properties.MODEM );
        if( modem != BlockCableModemVariant.None )
        {
            return modem.getFacing();
        }
        else
        {
            return EnumFacing.NORTH;
        }
    }

    @Override
    public void setDirection( EnumFacing dir )
    {
        IBlockState state = getBlockState();
        BlockCableModemVariant modem = state.getValue( BlockCable.Properties.MODEM );
        if( modem != BlockCableModemVariant.None )
        {
            setBlockState( state.withProperty( BlockCable.Properties.MODEM, BlockCableModemVariant.fromFacing( dir ) ) );
        }
    }

    @Override
    public void getDroppedItems( @Nonnull NonNullList<ItemStack> drops, boolean creative )
    {
        if( !creative )
        {
            PeripheralType type = getPeripheralType();
            switch( type )
            {
                case Cable:
                case WiredModem:
                {
                    drops.add( PeripheralItemFactory.create( type, getLabel(), 1 ) );
                    break;
                }
                case WiredModemWithCable:
                {
                    drops.add( PeripheralItemFactory.create( PeripheralType.WiredModem, getLabel(), 1 ) );
                    drops.add( PeripheralItemFactory.create( PeripheralType.Cable, null, 1 ) );
                    break;
                }
            }
        }
    }

    @Override
    public ItemStack getPickedItem()
    {
        if( getPeripheralType() == PeripheralType.WiredModemWithCable )
        {
            return PeripheralItemFactory.create( PeripheralType.WiredModem, getLabel(), 1 );
        }
        else
        {
            return super.getPickedItem();
        }
    }

    @Override
    public void onNeighbourChange()
    {
        EnumFacing dir = getDirection();
        if( !getWorld().isSideSolid(
            getPos().offset( dir ),
            dir.getOpposite()
        ) )
        {
            switch( getPeripheralType() )
            {
                case WiredModem:
                {
                    // Drop everything and remove block
                    ((BlockGeneric) getBlockType()).dropAllItems( getWorld(), getPos(), false );
                    getWorld().setBlockToAir( getPos() );

                    // This'll call #destroy(), so we don't need to reset the network here.
                    return;
                }
                case WiredModemWithCable:
                {
                    // Drop the modem and convert to cable
                    ((BlockGeneric) getBlockType()).dropItem( getWorld(), getPos(), PeripheralItemFactory.create( PeripheralType.WiredModem, getLabel(), 1 ) );
                    setLabel( null );
                    setBlockState( getBlockState().withProperty( BlockCable.Properties.MODEM, BlockCableModemVariant.None ) );
                    modemChanged();
                    connectionsChanged();

                    return;
                }
            }
        }

        if( !world.isRemote && m_peripheralAccessAllowed )
        {
            if( m_peripheral.attach( world, getPos(), dir ) ) updateConnectedPeripherals();
        }
    }

    @Override
    public void onNeighbourTileEntityChange( @Nonnull BlockPos neighbour )
    {
        super.onNeighbourTileEntityChange( neighbour );
        if( !world.isRemote && m_peripheralAccessAllowed )
        {
            EnumFacing facing = getDirection();
            if( getPos().offset( facing ).equals( neighbour ) )
            {
                if( m_peripheral.attach( world, getPos(), facing ) ) updateConnectedPeripherals();
            }
        }
    }

    public AxisAlignedBB getModemBounds()
    {
        return super.getBounds();
    }

    private AxisAlignedBB getCableBounds()
    {
        double xMin = 0.375;
        double yMin = 0.375;
        double zMin = 0.375;
        double xMax = 0.625;
        double yMax = 0.625;
        double zMax = 0.625;
        BlockPos pos = getPos();
        World world = getWorld();

        IBlockState state = getBlockState();
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.WEST ) )
        {
            xMin = 0.0;
        }
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.EAST ) )
        {
            xMax = 1.0;
        }
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.DOWN ) )
        {
            yMin = 0.0;
        }
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.UP ) )
        {
            yMax = 1.0;
        }
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.NORTH ) )
        {
            zMin = 0.0;
        }
        if( BlockCable.doesConnectVisually( state, world, pos, EnumFacing.SOUTH ) )
        {
            zMax = 1.0;
        }
        return new AxisAlignedBB( xMin, yMin, zMin, xMax, yMax, zMax );
    }

    @Nonnull
    @Override
    public AxisAlignedBB getBounds()
    {
        PeripheralType type = getPeripheralType();
        switch( type )
        {
            case WiredModem:
            default:
            {
                return getModemBounds();
            }
            case Cable:
            {
                return getCableBounds();
            }
            case WiredModemWithCable:
            {
                AxisAlignedBB modem = getModemBounds();
                AxisAlignedBB cable = getCableBounds();
                return modem.union( cable );
            }
        }
    }

    @Override
    public void getCollisionBounds( @Nonnull List<AxisAlignedBB> bounds )
    {
        PeripheralType type = getPeripheralType();
        if( type == PeripheralType.WiredModem || type == PeripheralType.WiredModemWithCable )
        {
            bounds.add( getModemBounds() );
        }
        if( type == PeripheralType.Cable || type == PeripheralType.WiredModemWithCable )
        {
            bounds.add( BOX_CENTRE );

            IBlockState state = getBlockState();
            for( EnumFacing facing : EnumFacing.VALUES )
            {
                if( BlockCable.doesConnectVisually( state, world, pos, facing ) )
                {
                    bounds.add( BOXES[facing.ordinal()] );
                }
            }
        }
    }

    @Override
    public boolean onActivate( EntityPlayer player, EnumFacing side, float hitX, float hitY, float hitZ )
    {
        if( getPeripheralType() == PeripheralType.WiredModemWithCable && !player.isSneaking() )
        {
            if( !getWorld().isRemote )
            {
                // On server, we interacted if a peripheral was found
                String oldPeriphName = m_peripheral.getConnectedName();
                togglePeripheralAccess();
                String periphName = m_peripheral.getConnectedName();

                if( !Objects.equal( periphName, oldPeriphName ) )
                {
                    if( oldPeriphName != null )
                    {
                        player.sendMessage(
                            new TextComponentTranslation( "gui.computercraft:wired_modem.peripheral_disconnected", oldPeriphName )
                        );
                    }
                    if( periphName != null )
                    {
                        player.sendMessage(
                            new TextComponentTranslation( "gui.computercraft:wired_modem.peripheral_connected", periphName )
                        );
                    }
                    return true;
                }
            }
            else
            {
                // On client, we can't know this, so we assume so to be safe
                // The server will correct us if we're wrong
                return true;
            }
        }
        return false;
    }

    @Override
    public void readFromNBT( NBTTagCompound nbttagcompound )
    {
        // Read properties
        super.readFromNBT( nbttagcompound );
        m_peripheralAccessAllowed = nbttagcompound.getBoolean( "peripheralAccess" );
        m_peripheral.readNBT( nbttagcompound, "" );
    }

    @Nonnull
    @Override
    public NBTTagCompound writeToNBT( NBTTagCompound nbttagcompound )
    {
        // Write properties
        nbttagcompound = super.writeToNBT( nbttagcompound );
        nbttagcompound.setBoolean( "peripheralAccess", m_peripheralAccessAllowed );
        m_peripheral.writeNBT( nbttagcompound, "" );
        return nbttagcompound;
    }

    @Override
    protected void updateAnim()
    {
        int anim = 0;
        if( m_modem.isActive() )
        {
            anim += 1;
        }
        if( m_peripheralAccessAllowed )
        {
            anim += 2;
        }
        setAnim( anim );
    }

    @Override
    public void update()
    {
        super.update();
        updateDirection();
        if( !getWorld().isRemote )
        {
            if( !m_connectionsFormed )
            {
                m_connectionsFormed = true;

                connectionsChanged();
                if( m_peripheralAccessAllowed )
                {
                    m_peripheral.attach( world, pos, m_dir );
                    updateConnectedPeripherals();
                }
            }
        }
    }

    public void connectionsChanged()
    {
        if( getWorld().isRemote ) return;

        IBlockState state = getBlockState();
        World world = getWorld();
        BlockPos current = getPos();
        for( EnumFacing facing : EnumFacing.VALUES )
        {
            BlockPos offset = current.offset( facing );
            if( !world.isBlockLoaded( offset ) ) continue;

            IWiredElement element = ComputerCraft.getWiredElementAt( world, offset, facing.getOpposite() );
            if( element == null ) continue;

            if( BlockCable.canConnectIn( state, facing ) )
            {
                // If we can connect to it then do so
                m_node.connectTo( element.getNode() );
            }
            else if( m_node.getNetwork() == element.getNode().getNetwork() )
            {
                // Otherwise if we're on the same network then attempt to void it.
                m_node.disconnectFrom( element.getNode() );
            }
        }
    }

    public void modemChanged()
    {
        if( getWorld().isRemote ) return;

        PeripheralType type = getPeripheralType();
        if( type != PeripheralType.WiredModemWithCable && m_peripheralAccessAllowed )
        {
            m_peripheralAccessAllowed = false;
            m_peripheral.detach();
            m_node.updatePeripherals( Collections.emptyMap() );
            markDirty();
            updateAnim();
        }
    }

    // private stuff
    private void togglePeripheralAccess()
    {
        if( !m_peripheralAccessAllowed )
        {
            m_peripheral.attach( world, getPos(), getDirection() );
            if( !m_peripheral.hasPeripheral() ) return;

            m_peripheralAccessAllowed = true;
            m_node.updatePeripherals( m_peripheral.toMap() );
        }
        else
        {
            m_peripheral.detach();

            m_peripheralAccessAllowed = false;
            m_node.updatePeripherals( Collections.emptyMap() );
        }

        updateAnim();
    }

    private void updateConnectedPeripherals()
    {
        Map<String, IPeripheral> peripherals = m_peripheral.toMap();
        if( peripherals.isEmpty() )
        {
            // If there are no peripherals then disable access and update the display state.
            m_peripheralAccessAllowed = false;
            updateAnim();
        }

        m_node.updatePeripherals( peripherals );
    }

    @Override
    public boolean canRenderBreaking()
    {
        return true;
    }

    // IWiredElement capability

    @Override
    public boolean hasCapability( @Nonnull Capability<?> capability, @Nullable EnumFacing facing )
    {
        if( capability == CapabilityWiredElement.CAPABILITY ) return BlockCable.canConnectIn( getBlockState(), facing );
        return super.hasCapability( capability, facing );
    }

    @Nullable
    @Override
    public <T> T getCapability( @Nonnull Capability<T> capability, @Nullable EnumFacing facing )
    {
        if( capability == CapabilityWiredElement.CAPABILITY )
        {
            return BlockCable.canConnectIn( getBlockState(), facing ) ? CapabilityWiredElement.CAPABILITY.cast( m_cable ) : null;
        }

        return super.getCapability( capability, facing );
    }

    // IPeripheralTile

    @Override
    public IPeripheral getPeripheral( EnumFacing side )
    {
        if( getPeripheralType() != PeripheralType.Cable )
        {
            return super.getPeripheral( side );
        }
        return null;
    }
}
