# Security policy

Report security-sensitive issues privately to the repository owner rather than
opening a public issue. Include the affected API, a minimal reproduction, and
the impact you observed.

bids4s parses untrusted filenames, JSON, and tabular metadata. Callers must
still apply ordinary filesystem permissions and data-governance controls.
Filesystem adapters confine relative project paths beneath the configured root;
they do not provide an authorization system.
