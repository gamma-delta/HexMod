package at.petrak.hexcasting.api.misc;

import net.minecraft.core.Direction;

public record GravitySetting(Direction gravityDirection, boolean permanent, int timeLeft) {
    public static GravitySetting deny() {
        return new GravitySetting(Direction.DOWN, true, 0);
    }
}
