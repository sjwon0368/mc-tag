package io.codejava.mc.tag;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;

import java.time.Duration;
import java.util.*;

public class TagGamePlugin extends JavaPlugin implements Listener, CommandExecutor {

    private Player tagger; // 플레이어 상태 태그, 좌표 등 데이터 설정
    private Player runner;
    private Location taggerOriginalLoc;
    private Location runnerOriginalLoc;
    private final Map<UUID, Location> deathLocations = new HashMap<>();
    private boolean gameRunning = false;

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("tagstart")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this); // 명령어, 이벤트 설정 추가 - 플러그인 실행 시
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (!p.isOp()) {
            p.sendMessage("§c이 명령어를 사용할 권한이 없습니다."); // op 유저만 사용 - 커맨드
            return true;
        }

        if (args.length != 2) {
            p.sendMessage("§c명령어 사용 방법:\n/tagstart <술래> <도망자>\n예시: /tagstart user1 user2"); // 도움말 출력
            return true;
        }

        var tag = Bukkit.getPlayerExact(args[0]);
        var run = Bukkit.getPlayerExact(args[1]); // 플레이어 데이터 불러오기

        if (tag == null || run == null || !tag.isOnline() || !run.isOnline()) {
            p.sendMessage("§c오류: 게임 시작 실패. 입력한 플레이어 이름 중 하나 이상이 존재하지 않거나 오프라인입니다."); // 오류 1 - 플레이어 존재하지 않음
            return true;
        }

        if (tag.equals(run)) {
            p.sendMessage("§c술래와 도망자가 동일할 수 없습니다."); // 오류 2 - 술래, 도망자 아이디가 같음
            return true;
        }

        tagger = tag;
        runner = run;
        taggerOriginalLoc = tag.getLocation(); // 게임 시작 전 좌표 불러오기기
        runnerOriginalLoc = run.getLocation();

        double distance = tag.getLocation().distance(run.getLocation());
        if (distance < 100) {
            p.sendMessage("§e두 플레이어 사이의 거리가 100블럭 이상이 아닙니다. 자동으로 랜덤 위치에 100블럭 이상의 거리로 순간이동합니다.");
            tag.teleport(tag.getWorld().getSpawnLocation().add(0, 2, 0)); // 100블럭 거리 내에 있는 경우 렌덤 텔포
            run.teleport(tag.getWorld().getSpawnLocation().add(150, 2, 0));
        }

        freezePlayer(tag);
        freezePlayer(run);

        tag.showTitle(Title.title(Component.text("술래잡기가 곧 시작됩니다.", NamedTextColor.YELLOW), Component.text(""),
                Title.Times.of(Duration.ofSeconds(1), Duration.ofSeconds(15), Duration.ofSeconds(1)))); // 게임 시작 안내

        new BukkitRunnable() {
            int seconds = 15;
            @Override
            public void run() {
                if (seconds <= 0) {
                    unfreezePlayer(tagger);
                    unfreezePlayer(runner);
                    tagger.sendActionBar(Component.text("게임 시작!", NamedTextColor.GREEN)); // 시작 알림
                    runner.sendActionBar(Component.text("게임 시작!", NamedTextColor.GREEN));
                    gameRunning = true;
                    cancel();
                    return;
                }
                String msg = "게임 시작까지 " + seconds + "초";
                tagger.sendActionBar(Component.text(msg)); // 핫바 위에 게임 시작까지 대기시간 표시시
                runner.sendActionBar(Component.text(msg));
                seconds--;
            }
        }.runTaskTimer(this, 0, 20);

        return true;
    }

    private void freezePlayer(Player p) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 999999, 255, false, false)); // 수정 - 게임 시작 전 움직임 방지
        p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 999999, 200, false, false));
    }

    private void unfreezePlayer(Player p) {
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.JUMP_BOOST); // 수정 - 게임 시작 후 포션효과 제거, 움직임 가능
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        if (!gameRunning) return;
        if (e.getPlayer().equals(tagger) || e.getPlayer().equals(runner)) {
            gameRunning = false;
            Bukkit.broadcastMessage("§c오류: 하나 이상의 플레이어가 게임을 나갔습니다. 게임이 종료됩니다."); // 오류 3 - 플레이어 중 한 명 이상이 나감감
            if (tagger != null) tagger.teleport(taggerOriginalLoc);
            if (runner != null) runner.teleport(runnerOriginalLoc);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (!gameRunning) return;
        Player p = e.getEntity();

        if (p.equals(tagger)) {
            Bukkit.broadcastMessage("§e술래가 사망했습니다. 1분 후 동일한 위치에서 리스폰됩니다."); // 술래 사망 - 1분 후 리스폰
            deathLocations.put(p.getUniqueId(), p.getLocation());
            scheduleRespawn(p, 60);
        } else if (p.equals(runner)) {
            Bukkit.broadcastMessage("§e도망자가 사망했습니다. 2분 후 동일한 위치에서 리스폰됩니다."); // 도망자 사망 - 2분 후 리스폰
            deathLocations.put(p.getUniqueId(), p.getLocation());
            scheduleRespawn(p, 120);
        }
    }

    @EventHandler
    public void onDragonKill(EntityDeathEvent e) {
        if (!gameRunning) return;
        if (e.getEntity() instanceof EnderDragon) {
            if (e.getEntity().getKiller() == null || e.getEntity().getKiller().equals(tagger)) {
                Bukkit.broadcastMessage("§c도망자의 승리 조건 실패. 게임 종료.");
            } else {
                Bukkit.broadcastMessage("§b도망자 승! 게임 종료"); // 승리 조건 확인 - 도망자
            }
            endGame();
        }
    }

    @EventHandler
    public void onRunnerKilled(EntityDeathEvent e) {
        if (!gameRunning) return;
        if (e.getEntity().equals(runner) && e.getEntity().getKiller() != null && e.getEntity().getKiller().equals(tagger)) {
            Bukkit.broadcastMessage("§a술래 승! 게임 종료"); // 승리 조건 확인 - 술래래
            endGame();
        }
    }

    @EventHandler
    public void onDragonDamaged(EntityDamageByEntityEvent event) {
        if (!gameRunning) return;
        if (event.getEntity() instanceof EnderDragon && event.getDamager() instanceof Player attacker) {
            if (attacker.equals(tagger)) {
                attacker.sendMessage("§c술래는 엔더 드래곤을 공격할 수 없습니다!"); // 술래는 엔더드레곤 공격 불가
                event.setCancelled(true);
            }
        }
    }

    private void scheduleRespawn(Player p, int seconds) {
        new BukkitRunnable() {
            int timer = seconds;
            @Override
            public void run() {
                if (timer <= 0) {
                    Location loc = deathLocations.remove(p.getUniqueId());
                    if (loc != null) p.spigot().respawn();
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            p.teleport(loc);
                        }
                    }.runTaskLater(TagGamePlugin.this, 1);
                    cancel();
                    return;
                }
                p.sendTitle("§c죽었습니다!", "§f리스폰까지 " + timer + "초", 0, 20, 0); // 리스폰 카운트다운
                timer--;
            }
        }.runTaskTimer(this, 20, 20);
    }

    private void endGame() {
        gameRunning = false;
        if (tagger != null) tagger.teleport(taggerOriginalLoc);
        if (runner != null) runner.teleport(runnerOriginalLoc); // 게임 종료시 플레이어들을 원래 위치로 텔포
    }
                                  }
