package vip.mseapp.mes.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MES 单号生成器：前缀 + yyyyMMdd + 4 位日内序号。
 *
 * <p>序号按「前缀 + 当日」从库内现存最大尾号播种（首个请求时一次，JdbcTemplate 只读）——
 * 此前纯内存 AtomicLong 重启归零，重启后生成的单号与当日已存单号（含逻辑删除墓碑）
 * 撞唯一键且重提无效（序号推进前每次都撞同一号）。多实例并发首插仍可能撞号，
 * 唯一键报错重提即可（播种后窗口极小）。
 */
@Component
public class MesCodeGenerator {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 前缀 → 单号所在表（播种查询用；白名单防注入） */
    private static final Map<String, String> PREFIX_TABLE = Map.of(
            "MO", "mes_order",
            "QC", "mes_qc_order",
            "QE", "mes_qc_exception",
            "RP", "mes_repair",
            "AL", "mes_andon_call",
            "MR", "mes_mat_req",
            "MRQ", "mes_mat_req");

    private final AtomicLong seq = new AtomicLong();

    private final JdbcTemplate jdbcTemplate;

    /** 已播种的「前缀@日」标记 */
    private final ConcurrentHashMap<String, Boolean> seeded = new ConcurrentHashMap<>();

    public MesCodeGenerator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 生成单号，如 next("MO") → MO202609240001 */
    public String next(String prefix) {
        seedOnce(prefix);
        long n = seq.incrementAndGet();
        // 序号超出当日 4 位容量时取纳秒尾数兜底，避免长时间运行溢出归零撞号
        if (n > 9999) {
            n = System.nanoTime() % 9000 + 1000;
        }
        return prefix + LocalDate.now().format(DAY) + String.format("%04d", n);
    }

    /** 生成批次号，如 B20260924-0001 */
    public String nextBatch() {
        seedOnce("B");
        return "B" + LocalDate.now().format(DAY) + "-" + String.format("%04d", seq.incrementAndGet() % 10000);
    }

    /** 按前缀播种当日序号：取库内当日该前缀最大 4 位尾号（含墓碑行），失败静默跳过走原行为 */
    private void seedOnce(String prefix) {
        String day = LocalDate.now().format(DAY);
        String key = prefix + "@" + day;
        if (seeded.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        try {
            String table = PREFIX_TABLE.get(prefix);
            if (table == null || jdbcTemplate == null) {
                return;
            }
            Long max = jdbcTemplate.queryForObject(
                    "SELECT MAX(CAST(RIGHT(code, 4) AS UNSIGNED)) FROM " + table + " WHERE code LIKE ?",
                    Long.class, prefix + day + "%");
            if (max != null) {
                seq.accumulateAndGet(max, Math::max);
            }
        } catch (Exception ignored) {
            // 播种失败（表未建/库未就绪）退回内存序号行为
        }
    }
}
