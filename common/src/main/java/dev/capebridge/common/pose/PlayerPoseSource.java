package dev.capebridge.common.pose;

import java.util.Optional;
import java.util.UUID;

/** Read-only boundary; snapshots are immutable, sampling is owned by the server adapter. */
public interface PlayerPoseSource {
    Optional<PlayerPoseSnapshot> latest(UUID player);
}
