package dev.replayforge.replay;
import java.util.UUID;
public final class ReplayNotFoundException extends RuntimeException { public ReplayNotFoundException(UUID id) { super("Replay not found: " + id); } }
