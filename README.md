# Neovim UI Test

[![Build Status](https://github.com/Sciss/NeovimUITest/workflows/Scala%20CI/badge.svg?branch=main)](https://github.com/Sciss/NeovimUITest/actions?query=workflow%3A%22Scala+CI%22)

## statement

Some tests for embedding Neovim in a Swing/Java2D UI written in Scala. Work in progress.
This project is (C)opyright 2021
by Hanns Holger Rutz. All rights reserved. ScalaOSC is released under 
the [GNU Lesser General Public License](https://github.com/Sciss/NeovimUITest/raw/main/LICENSE) v2.1+ and comes with
absolutely no warranties. To contact the author, send an e-mail to `contact at sciss.de`.

## requirements / installation

This project builds with sbt against Scala 2.13.

## setting up nvim

clone the repo and `git checkout -b stable stable` to select nvim 0.5.0. Build via

    sudo apt install ninja-build gettext libtool libtool-bin autoconf automake g++ pkg-config unzip curl
    make distclean
    make CMAKE_BUILD_TYPE=Release -j4
    sudo make install

(usually also install `cmake`, but Debian's might be too old)

nvim-metals: install 'packer':

    git clone https://github.com/wbthomason/packer.nvim\
      ~/.local/share/nvim/site/pack/packer/start/packer.nvim

use as `~/.config/nvim/init.lua`: https://github.com/scalameta/nvim-metals/discussions/39

after first start of nvim, update packer plugins via `:PackerSync`.

## useful stuff

nvim uses [msgpack](https://msgpack.org/) to format its communication in remote procedure calls (RPC).
[msgpack-rpc spec](https://github.com/msgpack-rpc/msgpack-rpc/blob/master/spec.md).
msgpack libraries for Scala:

- [msgpack4z](https://github.com/msgpack4z/msgpack4z-core) - active project, sadly based on ScalaZ
- [airframe](https://github.com/wvlet/airframe) - seems to include msgpack support, but only low level?
- [scodec-msgpack](https://github.com/xuwei-k/scodec-msgpack) - based on scodec with shapeless as additional 
   dependency; seems up-to-date. **Trying to use this one**.
- [msgpack4s](https://github.com/velvia/msgpack4s) - unclear if still maintained
  
Scala projects communicating with nvim:

- [neovim-scala](https://github.com/fuyumatsuri/neovim-scala) - old project (2016), comes with its own
  [msgpack library](https://github.com/fuyumatsuri/msgpack-rpc-scala)
