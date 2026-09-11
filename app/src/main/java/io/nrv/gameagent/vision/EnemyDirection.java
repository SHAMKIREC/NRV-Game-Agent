package io.nrv.gameagent.vision;

/** Coarse direction of the nearest detected enemy relative to the player's HUD anchor. */
public enum EnemyDirection {
    NONE,
    LEFT,
    RIGHT,
    UP,
    DOWN,
    UP_LEFT,
    UP_RIGHT,
    DOWN_LEFT,
    DOWN_RIGHT,
    CENTER
}
