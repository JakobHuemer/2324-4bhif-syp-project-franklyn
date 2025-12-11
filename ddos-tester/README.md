# DDOS-Tester

This ddos-tester is a client that simulates multiple openbox clients
sending frames to the server.

## Parameters

-   `spread`: controlls how spread out the client requests are where 1 is very
    spread out and 0 is all clients send at the same time
-   `clients`: how many clients are sending requests
-   `interval`: the time in seconds that passes between each request per client (defaults to 5s)
-   `noise`: how much of the image changes (delta) with 1 being 100% and 0 being 0%
-   `prefix`: a string that is prepended to the random hash-like name the user registers to the server

## How to run

### Requirements

<details>
<summary>Nix</summary>

```shell
nix develop
```

</details>

<details>
<summary>Other</summary>

-   Rust (1.90)
-   Cargo

</details>

## Run

Either run it with cargo directly:

```shell
cargo run -- <args>
```

or compile the binary and run it

```shell
cargo build
```
