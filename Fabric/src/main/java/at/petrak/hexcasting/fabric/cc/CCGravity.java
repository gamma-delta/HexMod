package at.petrak.hexcasting.fabric.cc;

import at.petrak.hexcasting.api.misc.GravitySetting;
import at.petrak.hexcasting.fabric.interop.gravity.OpChangeGravity;
import dev.onyxstudios.cca.api.v3.component.Component;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

public class CCGravity implements Component
{
    public static final String
            TAG_DIRECTION   = "direction",
            TAG_TIME_LEFT = "time_left",
            TAG_PERMANENT = "permanent";

    final Entity owner;
    private       GravitySetting gravitySetting = GravitySetting.deny();

    public CCGravity(Entity owner)
    {
        this.owner = owner;
    }

    public GravitySetting getGravitySetting()
    {
        return gravitySetting;
    }

    public void setGravitySetting(GravitySetting gravity)
    {
        this.gravitySetting = gravity;
    }

    @Override
    public void readFromNbt(CompoundTag tag)
    {
        var dirNum = tag.getInt(TAG_DIRECTION);
        if (dirNum == Direction.DOWN.get2DDataValue())
        {
            this.gravitySetting = GravitySetting.deny();
        }
        else
        {
            var permanent = tag.getBoolean(TAG_PERMANENT);
            var timeLeft = tag.getInt(TAG_TIME_LEFT);
            this.gravitySetting = new GravitySetting(Direction.from2DDataValue(dirNum), permanent, timeLeft);
            if(!permanent)
            {
                OpChangeGravity.EntitiesWithGravitas.Companion.getActiveGravityTimers().add(owner);
            }
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag)
    {
        tag.putInt(TAG_DIRECTION, this.gravitySetting.gravityDirection().get2DDataValue());
        if (this.gravitySetting.gravityDirection() != Direction.DOWN)
        {
            tag.putBoolean(TAG_PERMANENT, this.gravitySetting.permanent());
            tag.putInt(TAG_TIME_LEFT, this.gravitySetting.timeLeft());
        }
    }
}