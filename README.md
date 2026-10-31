# Sable High Altitude Transit

Server-side NeoForge 1.21.1 add-on for Sable 2.0.5.

When the lowest point of a physical construction is at world Y 400 or higher,
the construction remains an active Sable physics body without loading any
Minecraft chunks under it or ahead of it. The mod bypasses Sable's normal
world-chunk residency requirement only for that physical body.

In dimensions whose normal build ceiling is below Y 400, transit begins as
soon as the construction's lowest point is above that ceiling. Those chunks
cannot contain vanilla terrain, so generating them would only stall flight.

Players carried by an eligible construction do not create vanilla player
terrain tickets and their inherited Sable movement does not synchronously
generate destination chunks. Player movement, controls and Sable tracking
continue normally.

No block or item is required. Normal terrain handling resumes on descent.

Version 1.3.4 restores height-before-chunk checks for server block/fluid reads
above the dimension's actual build ceiling. Lithium 0.15.4's block-access
overwrite requests the chunk before checking height; flight diagnostics
identified synchronous waits here from player suffocation checks and Create
Propulsion exhaust raycasts. These empty-world reads now return vanilla void
air/empty fluid without requesting a chunk. No Lithium settings are changed.
All reads inside Sable's reserved plot grid remain untouched, as do terrain
reads below the build ceiling. Sable ray projection and collision are not
disabled. This is not a blanket "missing chunk equals air" substitution.
The performance summary includes emptySkyReadsSkipped to verify the new path.

Version 1.3.3 skips the entire per-body terrain residency loop in Sable's
PhysicsChunkTicketManager once both the current and previous complete body
bounds clear max(Y 400, dimension build ceiling + 21). The margin accounts
for Sable's 20-block vertical prediction and one-block collision padding.
Physics integration, forces, controls and body-to-body simulation still run.
The optimization does not create fake chunks or discard world chunk status
notifications: ground-level constructions still receive those notifications.

During transit, logs/latest.log receives a [Transit perf] summary approximately
every five seconds with mean/max server tick time and skipped terrain loops.
A daemon samples the server stack at most ten times per second only while a
tick exceeds 100 ms. Up to three most frequent stacks are included in the next
summary; memory is bounded to 32 distinct stacks and cleared on server stop.
These are diagnostic samples, not a guaranteed attribution of all CPU cost.

Build verification does not establish a 100-construction/20-TPS guarantee.
That requires an in-game load test on the intended modpack and hardware.
