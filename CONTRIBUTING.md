# Contributing

## General Guidelines

* Code review by a [maintainer](CODEOWNERS) is required for all pull requests.
* All checks (Travis and CodeClimate) are required to pass for a pull request. If you believe a CodeClimate check
  is invalid for a particular case (it happens) feel free to reach out on Gitter to make a case for marking it ignored.
* Code is expected to follow the [google java style](https://google.github.io/styleguide/javaguide.html) with the
  exception of 120 char line limit.
* New code should be unit tested.
* New functionality should be minimally happy-path tested in integration tests.
* All pull requests are required to follow the DCO process by adding the following to the commit message
  ```
  Signed-off-by: John Doe <jdoe@jdoe.net>
  ```
  By adding the above, you agree to the [following](https://developercertificate.org/):
  ```
  Developer's Certificate of Origin 1.1

  By making a contribution to this project, I certify that:

  (a) The contribution was created in whole or in part by me and I
      have the right to submit it under the open source license
      indicated in the file; or

  (b) The contribution is based upon previous work that, to the best
      of my knowledge, is covered under an appropriate open source
      license and I have the right under that license to submit that
      work with modifications, whether created in whole or in part
      by me, under the same open source license (unless I am
      permitted to submit under a different license), as indicated
      in the file; or

  (c) The contribution was provided directly to me by some other
      person who certified (a), (b) or (c) and I have not modified
      it.

  (d) I understand and agree that this project and the contribution
      are public and that a record of the contribution (including all
      personal information I submit with it, including my sign-off) is
      maintained indefinitely and may be redistributed consistent with
      this project or the open source license(s) involved.
  ```

## Requirements

* Java 8
* Gradle 4
* Docker
* docker-compose

## Building

* Compile
  * `gradle assemble`
* Unit Tests
  * `gradle test`
* Integration Tests
  * `gradle integrationTest`
* Bringing up a Go.cd server/agent pair with latest code
  * `gradle composeUp`
  * `gradle composeDown`
  * *note* - By default these use version 17.3.0, this can be changed using the GOCD_VERSION environment variable.
