package me.morok.fControlPlayer.control;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class PlayerStateSnapshot {
    private final Location location;
    private final boolean allowFlight;
    private final boolean flying;
    private final boolean invulnerable;
    private final boolean invisible;
    private final boolean collidable;
    private final boolean gravity;
    private final GameMode gameMode;
    private final float walkSpeed;
    private final float flySpeed;
    private final double absorptionAmount;

    private PlayerStateSnapshot(Location location, boolean allowFlight, boolean flying, boolean invulnerable,
                                boolean invisible, boolean collidable, boolean gravity, GameMode gameMode,
                                float walkSpeed, float flySpeed, double absorptionAmount) {
        this.location = location;
        this.allowFlight = allowFlight;
        this.flying = flying;
        this.invulnerable = invulnerable;
        this.invisible = invisible;
        this.collidable = collidable;
        this.gravity = gravity;
        this.gameMode = gameMode;
        this.walkSpeed = walkSpeed;
        this.flySpeed = flySpeed;
        this.absorptionAmount = absorptionAmount;
    }

    public static PlayerStateSnapshot capture(Player player) {
        return new PlayerStateSnapshot(
            player.getLocation().clone(),
            player.getAllowFlight(),
            player.isFlying(),
            player.isInvulnerable(),
            player.isInvisible(),
            player.isCollidable(),
            player.hasGravity(),
            player.getGameMode(),
            player.getWalkSpeed(),
            player.getFlySpeed(),
            player.getAbsorptionAmount()
        );
    }

    public void restore(Player player) {
        if (location != null) {
            player.teleport(location, PlayerTeleportEvent.TeleportCause.PLUGIN);
        }
        player.setAllowFlight(allowFlight);
        player.setFlying(flying);
        player.setInvulnerable(invulnerable);
        player.setInvisible(invisible);
        player.setCollidable(collidable);
        player.setGravity(gravity);
        if (gameMode != null) {
            player.setGameMode(gameMode);
        }
        player.setWalkSpeed(walkSpeed);
        player.setFlySpeed(flySpeed);
        player.setAbsorptionAmount(absorptionAmount);
    }
}
