package mx.mundolap;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Reloj controlable para probar vencimientos sin esperar en tiempo real. */
public final class RelojPrueba extends Clock {
    private Instant ahora = Instant.parse("2026-11-06T12:00:00Z");

    public void avanzar(Duration d) { ahora = ahora.plus(d); }

    @Override public ZoneId getZone() { return ZoneOffset.UTC; }

    @Override public Clock withZone(ZoneId zone) { return this; }

    @Override public Instant instant() { return ahora; }
}
