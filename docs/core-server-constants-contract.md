# Core-Server Constants Contract

Purpose: keep Core and Server aligned on board geometry and movement constants.

## Canonical Source

Use `no.ntnu.ping404.utils.Constants` as the single source of truth for:

- Board dimensions (`BOARD_WIDTH`, `BOARD_HEIGHT`, `BOARD_MIN_X`, `BOARD_MIN_Y`)
- Goal geometry (`GOAL_WIDTH`, `GOAL_HEIGHT`, `goalTop(...)`, `goalBottom(...)`)
- Puck geometry and speed (`PUCK_RADIUS`, `INITIAL_PUCK_SPEED`)
- Paddle geometry and speed (`PADDLE_WIDTH`, `PADDLE_HEIGHT`, `PADDLE_MARGIN`, `PADDLE_DEFAULT_SPEED`)

## Coordination Notes for Assigned Server Developer

1. Avoid introducing local numeric literals for board/goal/paddle/puck values in server handlers.
2. Use `Constants.boardCenterX()` and `Constants.boardCenterY()` for slot/half validation and spawn placement.
3. Keep winner/game-over flow using the same board and goal references when goal detection is wired into server tick flow.
4. If a gameplay value changes, update `Constants` first and verify both `:core` and `:server` tests.
5. `GameTickProcessor` uses `Constants.boardCenterX/Y()` to reset the puck after a goal. Do not hardcode center coordinates. (#18)
6. `CollisionDetector.resolveTick()` is the single Core entry point for all collision rules. `GameTickProcessor` CALLS it -- never reimplements wall/paddle/goal logic. (#18)

## Related Work

- Winner/GameOver: #15, #16
- Score behavior/reset: #10, #11
- Server-side game loop orchestration: #18 (GameTickProcessor, GameLoop, InputQueue)
