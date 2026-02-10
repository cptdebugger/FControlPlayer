package me.morok.fControlPlayer.control;

import org.bukkit.potion.PotionEffect;

final class EffectSnapshot {
    private final int amplifier;
    private final boolean ambient;
    private final boolean particles;
    private final boolean icon;
    private int duration;

    private EffectSnapshot(int amplifier, int duration, boolean ambient, boolean particles, boolean icon) {
        this.amplifier = amplifier;
        this.duration = duration;
        this.ambient = ambient;
        this.particles = particles;
        this.icon = icon;
    }

    static EffectSnapshot from(PotionEffect effect) {
        return new EffectSnapshot(
            effect.getAmplifier(),
            effect.getDuration(),
            effect.isAmbient(),
            effect.hasParticles(),
            effect.hasIcon()
        );
    }

    int getAmplifier() {
        return amplifier;
    }

    int getDuration() {
        return duration;
    }

    boolean isAmbient() {
        return ambient;
    }

    boolean hasParticles() {
        return particles;
    }

    boolean hasIcon() {
        return icon;
    }

    void tickDown() {
        if (duration > 0) {
            duration--;
        }
    }

    boolean shouldResend(EffectSnapshot current) {
        if (current == null) {
            return false;
        }
        if (amplifier != current.amplifier
            || ambient != current.ambient
            || particles != current.particles
            || icon != current.icon) {
            return true;
        }
        return current.duration > duration + 2;
    }
}
