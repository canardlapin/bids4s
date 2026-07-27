# Provenance

bids4s was extracted from `modules/bids` in
[`canardlapin/scalafim`](https://github.com/canardlapin/scalafim).

The extraction source was:

- ScalaFIM commit: `6e96e7885b5d682539c3e06bbe044dd88867d87a`
- latest commit touching `modules/bids`:
  `b70769eb41a85a5e2bfce9bf2371dd7aab2f8556`
- source boundary: `modules/bids`
- design record: `docs/plans/bids-functional-hardening.md`

The extraction copied the source and tests, renamed `scalafim.bids` to
`bids4s`, renamed `private[bids]` qualifiers to `private[bids4s]`, and added a
standalone build and project documentation. It did not intentionally change
domain behavior.

Future bids4s development is independent. ScalaFIM and Eidolon consume bids4s
as downstream libraries and do not own its public API.
