#!/usr/bin/env bash
# One-time LuckPerms setup for Blade ranks. Run while server is online via console or: bash setup-blade-ranks.sh | nc ...
set -euo pipefail

commands() {
  cat <<'CMDS'
lp creategroup default
lp group default setweight 0
lp creategroup trial
lp group trial meta setprefix 100 "§f "
lp group trial setweight 10
lp creategroup booster
lp group booster meta setprefix 100 "§f "
lp group booster setweight 20
lp creategroup chamber
lp group chamber meta setprefix 100 "§f "
lp group chamber setweight 30
lp creategroup razor
lp group razor meta setprefix 100 "§f "
lp group razor setweight 40
lp group razor permission set blade.mute true
lp group razor permission set blade.mute.max.3600 true
lp creategroup winner
lp group winner meta setprefix 100 "§f "
lp group winner setweight 50
lp group winner permission set blade.mute true
lp group winner permission set blade.mute.max.10800 true
lp creategroup sponsor
lp group sponsor meta setprefix 100 "§f "
lp group sponsor setweight 60
lp group sponsor permission set blade.mute true
lp group sponsor permission set blade.mute.max.21600 true
lp creategroup media
lp group media meta setprefix 100 "§f "
lp group media setweight 65
lp creategroup stazher
lp group stazher meta setprefix 100 "§f "
lp group stazher setweight 70
lp group stazher permission set blade.mute true
lp group stazher permission set blade.mute.max.604800 true
lp group stazher permission set blade.ban true
lp group stazher permission set blade.ban.max.604800 true
lp creategroup helper
lp group helper meta setprefix 100 "§f "
lp group helper setweight 80
lp group helper permission set blade.mute true
lp group helper permission set blade.mute.unlimited true
lp group helper permission set blade.ban true
lp group helper permission set blade.ban.max.1209600 true
lp group helper permission set blade.ban.ip true
lp group helper permission set blade.unmute true
lp group helper permission set blade.unban true
lp creategroup moder
lp group moder meta setprefix 100 "§f "
lp group moder setweight 90
lp group moder permission set blade.mute true
lp group moder permission set blade.mute.unlimited true
lp group moder permission set blade.ban true
lp group moder permission set blade.ban.max.2592000 true
lp group moder permission set blade.ban.ip true
lp group moder permission set blade.unmute true
lp group moder permission set blade.unban true
lp creategroup stmoder
lp group stmoder meta setprefix 100 "§f "
lp group stmoder setweight 100
lp group stmoder permission set blade.mute true
lp group stmoder permission set blade.mute.unlimited true
lp group stmoder permission set blade.ban true
lp group stmoder permission set blade.ban.max.15552000 true
lp group stmoder permission set blade.ban.ip true
lp group stmoder permission set blade.unmute true
lp group stmoder permission set blade.unban true
lp creategroup glmoder
lp group glmoder meta setprefix 100 "§f "
lp group glmoder setweight 110
lp group glmoder permission set blade.mute true
lp group glmoder permission set blade.mute.unlimited true
lp group glmoder permission set blade.ban true
lp group glmoder permission set blade.ban.permanent true
lp group glmoder permission set blade.ban.ip true
lp group glmoder permission set blade.unmute true
lp group glmoder permission set blade.unban true
lp creategroup dizainer
lp group dizainer meta setprefix 100 "§f "
lp group dizainer setweight 115
lp creategroup tehadmin
lp group tehadmin meta setprefix 100 "§f "
lp group tehadmin setweight 118
lp creategroup kurator
lp group kurator meta setprefix 100 "§f "
lp group kurator setweight 120
lp group kurator permission set blade.rank.grant true
lp group kurator permission set blade.mute true
lp group kurator permission set blade.mute.unlimited true
lp group kurator permission set blade.ban true
lp group kurator permission set blade.ban.max.1209600 true
lp group kurator permission set blade.ban.ip true
lp group kurator permission set blade.unmute true
lp group kurator permission set blade.unban true
lp creategroup zamestitel
lp group zamestitel meta setprefix 100 "§f "
lp group zamestitel setweight 130
lp group zamestitel permission set blade.op.grant true
lp group zamestitel permission set blade.rank.grant true
lp group zamestitel permission set blade.admin true
lp creategroup owner
lp group owner meta setprefix 100 "§f "
lp group owner setweight 140
lp group owner permission set blade.admin true
lp group default permission set group.default true
CMDS
}

commands
