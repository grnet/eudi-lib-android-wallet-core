# GRNET fork

This is multipaz's matcher, `multipaz-dcapi/src/androidMain/matcher` at multipaz
`0.101.0`, with one change. Built unchanged with WASI SDK 20, it is
byte-identical to the `identitycredentialmatcher.wasm` that wallet-core and
multipaz ship.

**The change: a TS12 card payment is shown as a payment.** When an OpenID4VP
request carries exactly one `transaction_data` of type
`urn:eudi:sca:payment:1`, with the payee's name, a numeric amount and a
currency, the credential that its `credential_ids` name is added to the
selector with `AddPaymentEntryToSetV2`, Credential Manager's payment entry:
the payee as the merchant, the amount (as `38.00 EUR`, padded to the
currency's minor units and never rounded), and the credential's title,
subtitle and icon as the card. The other credentials are shown as before.
A request without such a payment is matched exactly as upstream does.

- The entry id is the one upstream gives the credential, and the wallet sees
  the same selection.
- `AddPaymentEntryToSetV2` is in `credman_v2`, as in CMWallet's matcher,
  which renders the payment sheet. It is used only from Credential Manager's
  runtime version 2, the one that has sets.
- Each match now carries the id of the DCQL credential query it answers,
  which `credential_ids` refers to (`dcql.h`, `dcql.cpp`).

Every change is marked `GRNET fork`. To see the patch:
`git diff <commit that added this directory> -- wallet-core/matcher`.

To build it into wallet-core:

```shell
$ make clean && make -j && cp build/matcher.wasm ../src/main/assets/identitycredentialmatcher.wasm
```

`test/test.sh` builds the same sources, with stub Credential Manager
functions that print what the matcher reports, and checks the payment entries
on requests from `test/fixtures.py`. It needs the WASI SDK, Node 20 or later,
and python3.

# Upstream README

This directory contains a Credman matcher written in C/C++.

To compile it you need the [WASI SDK](https://github.com/WebAssembly/wasi-sdk/releases)
toolchain installed, specifically version 20. It should be installed in `~/wasi-sdk-20.0`.

The bundled `Makefile` will build the `build/matcher.wasm` binary which can be copied
into `../assets/identitycredentialmatcher.wasm` where it will get picked up as part
of the multipaz-dcapi library. The following command-line does this

```shell
$ make clean && make -j && cp build/matcher.wasm ../assets/identitycredentialmatcher.wasm
```

The [cJSON library](https://github.com/DaveGamble/cJSON) is bundled as `cJSON.[c, h]` with
license in `cJSON-LICENSE` file.

The [LibCppBor library](https://android.googlesource.com/platform/system/libcppbor/) is
bundled as `cppbor.[cpp, h]` and `cppbor_parse.[cpp, h]`. This is licensed under the Apache
License, Version 2.0.
