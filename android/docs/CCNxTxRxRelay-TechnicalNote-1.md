Overview
========

The purpose of the CCNxTxRxRelay is to discuss the rationale for an improved design of the old CCNx Android Services.  The original services were designed with the purpose of supporting internal PARC projects and to help demonstrate CCNx functionality on Mobile handsets in the early days of Android, api 7 era.  Android has since advanced rapidly.  The current release of Android in produciton is 4.4 (Kit Kat).  The existing CCNx Android Services are, bluntly, are not production grade, and even challenging to use for experimental work.

Author
======

David J. Kordsmeier
Razortooth Communications, LLC
github: truedat101
twitter: @dkords
irc: truedat101

Copyright
=========
This document is released under the CC-BY-SA 4.0 License: http://creativecommons.org/licenses/by-sa/4.0/ .  Feel free to share, remix, and distribute.

The Problem
===========

Below is an enumeration of the issues in the current design of the CCNx Android Services:

1. The problem with the existing design, first and foremost, is the lack of stability
1. The existing design does not feature a useful default configuration
1. The existing design does not support any persistent configuration between runs
1. The existing design isn't practical for production use, due to the prior three reasons
1. The existing design may not be efficient or resource friendly
1. The existing design may not be up to date with the latest capabilities in the CCNx stack
1. The existing design may not be taking full advantage of Android APIs and capabilities
1. The existing design is not up to date with the latest "state of the art" or "state of security" in cryptography, namely, Bouncy Castle Provider or the underlying OpenSSL
1. The existing design is poorly documented
1. The existing design is poorly distributed

The Solution
============

The author will not propose throwing out everything and starting over, though that will an alternative mentioned below.  A systematic addressing of each problem above is called for.  If one could achieve the first three items, the fourth item is won for free.  Anything else achieved in the list can be addressed in an iterative timeframe.  The initial goal should be to make it usable for production.

## Step 1 - Rebrand

To differentiate from the original, and to distance the new design from the old, flakey version, it is important to just call it something else.  The proposed name is CCNxTxRxRelay.  It may change.

## Step 2 - Document the list of known crashes

We don't yet have a clear documenting of all of the conditions that lead to CCNx Android Service crashes.  We need this list.  Each crash condition should get the following:

- Precondition
- Crash Summary
- Postcondition
- Stack Trace
- Log files
- Affected Android Services Subsystem
- Test Device OS Version
- Test Device Model #
- Test Device ABI

## Step 3 - Triage Known Crashes

The author estimatest there might be as many as 10-15 conditions.  We need these sorted into items that are OS issues, device specific issues, and then Android Service implementation issues, and finally Java and C-native api issues.  Mark issues with P1-P3.

## Step 4 - Implement Stability Bug Fixes

There is a clear objective here to eliminate all P1, P2 stability bugs.  Add a test harness to help eliminate regressions and make sure all fixes are verified.

## Step 5 - Implement Configuration Persistence

The configuration, however set, should persist between CCNx runs.

## Step 6 - Implement "Remote" Configuration

Aside from setting log levels, the configuration should be read from CCNx itself, allowing any sort of interface to push configuration.  The CCNx Services app should offer a way to turn on/off the remote configuration option.  It should be possible for an OEM deployer to set their own keys which must be used to sign the configuration prefix.  This would prevent a rougue party from pushing insecure configurations, or from a CCND from getting configurations "leaked" by neighboring nodes from other deployments.

## Step 7 - Implement "Local" Configuration

Taking a leaf from the Android Logback project, we should offer a way to configure CCNx Android Services using either a Configuration File, directly in the Android Manifest, or even in the code.  This will enable a variety of options for configuration, either by deployment using a manifest file or in a config file.

## Step 8 - Document all changes and new features

We need some docs describing how to build, configure, deploy, and use the CCNx Android Services.

## Step 9 - Unit Tests, Integration tests, Stress Tests

There was never any real testing other than a lone system test done before shipping a realse.  We need to divide the tests performed between:

- Unit Tests
- Integration Tests
- Stress Tests

Most of this can be acheived using various Android Intstrumentation test classes.

Alternative 1
=============

Throw out the current design.  Write a new design from scratch, without looking at the existing design.  While this may be tempting, it discards the years of knowledge that went into building the existing design.  It can be argued that the original creators didn't know what they were doing.  The author would argue they knew what they needed, and were working around old limitations and bugs in pre-ICS Android.  The existing work can be leveraged.  Tossing it all out would be a good idea if there were unlimited time and resources, but there is not, so this is not the right approach.


Alternative 2
=============

Rebuild the design around the model of an NDK native service, no Java code.  It might be easier to acheive a solid design that closely resembles the native C implementation we already have.  This option presents an interesting approach.  It should be possible to create something more immediately stable.  This approach is quite similar to Alternative 1, as it requires tossing out the existing design.  The author believes there is a strong case to be made for not doing this, for starters, not enough time or resources.

Alternative 3
=============

Break the design into a single service platform capable of dynamically loading new services, like an OSGI bootstrap, capable of fetching everything from a bootstrap process using CCNx protocol.

Conclusion
==========

A makeover of the existing CCNx Android Services is a first step towards making it possible to use CCNx in production on Android.  The first step is to fix existing P1-P2 bugs.  The follow on steps involve creating a configurable service, and testing.  Documentation will wrap up the package.
