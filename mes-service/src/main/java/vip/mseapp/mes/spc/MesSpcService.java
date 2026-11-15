package vip.mseapp.mes.spc;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vip.mseapp.core.exception.ServiceException;
import vip.mseapp.core.result.PageResult;
import vip.mseapp.mes.entity.MesSpcItem;
import vip.mseapp.mes.entity.MesSpcSample;
import vip.mseapp.mes.mapper.MesSpcItemMapper;
import vip.mseapp.mes.mapper.MesSpcSampleMapper;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * SPC 统计过程控制（三期）：监控项（规格限/子组配置）+ 样本测量值 → X̄-R 控制图、
 * 超限报警、过程能力指数 Cp/Cpk。
 *
 * <p>统计口径：按采样时间排序后每 n 个样本一组（n=监控项 sample_size）；
 * 控制限 X̄ 图 = X̄̄ ± A2·R̄，R 图 UCL = D4·R̄、CL = R̄（D3=0）；
 * σ̂ = R̄/d2；Cp = (USL-LSL)/(6σ̂)、Cpk = min(USL-X̄̄, X̄̄-LSL)/(3σ̂)（双侧规格限齐备时计算）。
 * 子组系数表内置（n=2..10，Shewhart 常数）。
 */
@Service
public class MesSpcService {

    /** Shewhart 常数（下标 = 子组大小 n；A2 / D3 / D4 / d2） */
    private static final double[][] CONSTANTS = {
            {0, 0, 0, 0},
            {0, 0, 0, 0},
            {1.880, 0, 3.267, 1.128},
            {1.023, 0, 2.574, 1.693},
            {0.729, 0, 2.282, 2.059},
            {0.577, 0, 2.114, 2.326},
            {0.483, 0, 2.004, 2.534},
            {0.419, 0.076, 1.924, 2.704},
            {0.373, 0.136, 1.864, 2.847},
            {0.337, 0.184, 1.816, 2.970},
            {0.308, 0.223, 1.777, 3.078}
    };

    private final MesSpcItemMapper itemMapper;
    private final MesSpcSampleMapper sampleMapper;

    public MesSpcService(MesSpcItemMapper itemMapper, MesSpcSampleMapper sampleMapper) {
        this.itemMapper = itemMapper;
        this.sampleMapper = sampleMapper;
    }

    // ==================== 监控项 ====================

    /** 监控项分页 */
    public PageResult<MesSpcItem> pageItems(long pageNum, long pageSize, String keyword) {
        LambdaQueryWrapper<MesSpcItem> query = new LambdaQueryWrapper<MesSpcItem>()
                .like(keyword != null && !keyword.isBlank(), MesSpcItem::getName, keyword)
                .orderByDesc(MesSpcItem::getId);
        Page<MesSpcItem> page = itemMapper.selectPage(Page.of(pageNum, pageSize), query);
        return PageResult.of(page.getTotal(), pageNum, pageSize, page.getRecords());
    }

    /** 监控项清单（启用项，样本录入/控制图选择器） */
    public List<MesSpcItem> listItems() {
        return itemMapper.selectList(new LambdaQueryWrapper<MesSpcItem>()
                .eq(MesSpcItem::getStatus, 1).orderByAsc(MesSpcItem::getCode));
    }

    /** 新增/编辑监控项（id 空=创建） */
    @Transactional(rollbackFor = Exception.class)
    public Long saveItem(Long id, String code, String name, Long materialId, String stepName,
                         String featureName, String unit, BigDecimal usl, BigDecimal lsl,
                         BigDecimal target, Integer sampleSize, Integer status, String remark) {
        if (code == null || code.isBlank() || name == null || name.isBlank()) {
            throw new ServiceException(400, "编码与名称必填");
        }
        int n = sampleSize == null ? 5 : sampleSize;
        if (n < 2 || n >= CONSTANTS.length) {
            throw new ServiceException(400, "子组大小仅支持 2-10");
        }
        if (usl == null && lsl == null) {
            throw new ServiceException(400, "规格上限/下限至少填一项");
        }
        MesSpcItem item = id == null ? new MesSpcItem() : itemMapper.selectById(id);
        if (item == null) {
            throw new ServiceException(1404, "监控项不存在");
        }
        MesSpcItem sameCode = itemMapper.selectOne(new LambdaQueryWrapper<MesSpcItem>()
                .eq(MesSpcItem::getCode, code).last("LIMIT 1"));
        if (sameCode != null && (id == null || !sameCode.getId().equals(id))) {
            throw new ServiceException(1409, "监控项编码已存在: " + code);
        }
        item.setCode(code);
        item.setName(name);
        item.setMaterialId(materialId);
        item.setStepName(stepName);
        item.setFeatureName(featureName);
        item.setUnit(unit);
        item.setUsl(usl);
        item.setLsl(lsl);
        item.setTarget(target);
        item.setSampleSize(n);
        item.setStatus(status == null ? 1 : status);
        item.setRemark(remark);
        if (id == null) {
            itemMapper.insert(item);
        } else {
            itemMapper.updateById(item);
        }
        return item.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public void deleteItem(Long id) {
        MesSpcItem item = itemMapper.selectById(id);
        if (item == null) {
            return;
        }
        sampleMapper.delete(new LambdaQueryWrapper<MesSpcSample>().eq(MesSpcSample::getItemId, id));
        itemMapper.deleteById(id);
    }

    // ==================== 样本 ====================

    /** 批量录入样本（单件或子组整批；source 1 手工 2 数采） */
    @Transactional(rollbackFor = Exception.class)
    public int addSamples(Long itemId, List<BigDecimal> values, String orderCode, Integer source,
                          LocalDateTime collectTime) {
        MesSpcItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new ServiceException(1404, "监控项不存在");
        }
        if (values == null || values.isEmpty()) {
            throw new ServiceException(400, "测量值不能为空");
        }
        LocalDateTime time = collectTime == null ? LocalDateTime.now() : collectTime;
        int count = 0;
        for (BigDecimal value : values) {
            if (value == null) {
                continue;
            }
            MesSpcSample sample = new MesSpcSample();
            sample.setItemId(itemId);
            sample.setValue(value);
            sample.setOrderCode(orderCode);
            sample.setSource(source == null ? MesSpcSample.SOURCE_MANUAL : source);
            sample.setCollectTime(time.plusSeconds(count));
            sampleMapper.insert(sample);
            count++;
        }
        return count;
    }

    // ==================== 控制图与能力 ====================

    /**
     * 控制图数据（X̄-R）：近 N 天样本按时间分组 → 子组统计 + 控制限 + 超限标记 + Cp/Cpk。
     * <p>样本不足两个完整子组时返回无控制限的原始分组（前端提示样本不足）。
     */
    public Map<String, Object> chart(Long itemId, int days) {
        MesSpcItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new ServiceException(1404, "监控项不存在");
        }
        int n = item.getSampleSize() == null ? 5 : item.getSampleSize();
        LocalDateTime start = LocalDateTime.now().minusDays(Math.min(Math.max(days, 1), 90));
        List<MesSpcSample> samples = sampleMapper.selectList(new LambdaQueryWrapper<MesSpcSample>()
                .eq(MesSpcSample::getItemId, itemId)
                .ge(MesSpcSample::getCollectTime, start)
                .orderByAsc(MesSpcSample::getCollectTime));
        // 分组（每组 n 个；尾部不足 n 的子组不参与控制限计算但保留展示）
        List<List<MesSpcSample>> groups = new ArrayList<>();
        for (int i = 0; i + n <= samples.size(); i += n) {
            groups.add(samples.subList(i, i + n));
        }
        int tail = samples.size() % n;
        double[] cl = controlLimits(item, groups, n);

        List<Map<String, Object>> points = new ArrayList<>();
        int outOfControl = 0;
        int outOfSpec = 0;
        for (int g = 0; g < groups.size(); g++) {
            List<MesSpcSample> group = groups.get(g);
            double mean = group.stream().mapToDouble(s -> s.getValue().doubleValue()).average().orElse(0);
            double range = group.stream().mapToDouble(s -> s.getValue().doubleValue()).max().orElse(0)
                    - group.stream().mapToDouble(s -> s.getValue().doubleValue()).min().orElse(0);
            boolean xBarViolate = cl[0] != 0 && (mean > cl[1] || mean < cl[2]);
            boolean rViolate = cl[4] != 0 && range > cl[4];
            if (xBarViolate || rViolate) {
                outOfControl++;
            }
            for (MesSpcSample s : group) {
                double v = s.getValue().doubleValue();
                boolean specViolate = (item.getUsl() != null && v > item.getUsl().doubleValue())
                        || (item.getLsl() != null && v < item.getLsl().doubleValue());
                if (specViolate) {
                    outOfSpec++;
                }
            }
            Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("group", g + 1);
            point.put("mean", round(mean));
            point.put("range", round(range));
            point.put("xBarViolate", xBarViolate);
            point.put("rViolate", rViolate);
            point.put("collectTime", String.valueOf(group.get(0).getCollectTime()));
            points.add(point);
        }
        // 尾部不完整子组（展示为未评定）
        if (tail > 0) {
            List<MesSpcSample> rest = samples.subList(samples.size() - tail, samples.size());
            Map<String, Object> point = new java.util.LinkedHashMap<>();
            point.put("group", groups.size() + 1);
            point.put("mean", round(rest.stream().mapToDouble(s -> s.getValue().doubleValue()).average().orElse(0)));
            point.put("range", null);
            point.put("xBarViolate", false);
            point.put("rViolate", false);
            point.put("incomplete", true);
            point.put("collectTime", String.valueOf(rest.get(0).getCollectTime()));
            points.add(point);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("item", item);
        result.put("days", Math.min(Math.max(days, 1), 90));
        result.put("sampleSize", n);
        result.put("totalSamples", samples.size());
        result.put("groups", points);
        result.put("outOfControl", outOfControl);
        result.put("outOfSpec", outOfSpec);
        result.put("limits", cl[0] == 0 ? null : Map.of(
                "xBar", round(cl[0]), "xBarUcl", round(cl[1]), "xBarLcl", round(cl[2]),
                "rBar", round(cl[3]), "rUcl", round(cl[4])));
        result.put("capability", cl[0] == 0 ? null : capability(item, cl));
        return result;
    }

    /** 控制限：{X̄̄, X̄UCL, X̄LCL, R̄, RUCL}；完整子组 < 2 时全 0（样本不足） */
    private double[] controlLimits(MesSpcItem item, List<List<MesSpcSample>> groups, int n) {
        if (groups.size() < 2) {
            return new double[5];
        }
        double[] c = CONSTANTS[n];
        double grandMean = 0;
        double rBar = 0;
        for (List<MesSpcSample> group : groups) {
            double max = -Double.MAX_VALUE;
            double min = Double.MAX_VALUE;
            for (MesSpcSample s : group) {
                double v = s.getValue().doubleValue();
                grandMean += v;
                max = Math.max(max, v);
                min = Math.min(min, v);
            }
            rBar += max - min;
        }
        grandMean /= groups.size() * (double) n;
        rBar /= groups.size();
        return new double[]{grandMean, grandMean + c[0] * rBar, grandMean - c[0] * rBar, rBar, c[2] * rBar};
    }

    /** 过程能力：σ̂=R̄/d2；Cp/Cpk 仅双侧规格限齐备时给出（单侧只给 Cpk 上/下限单边） */
    private Map<String, Object> capability(MesSpcItem item, double[] cl) {
        int n = item.getSampleSize() == null ? 5 : item.getSampleSize();
        double sigma = cl[3] / CONSTANTS[n][3];
        if (sigma <= 0) {
            return Map.of("sigma", 0, "cp", null, "cpk", null);
        }
        double mean = cl[0];
        Map<String, Object> capability = new java.util.LinkedHashMap<>();
        capability.put("sigma", round(sigma));
        if (item.getUsl() != null && item.getLsl() != null) {
            double cp = (item.getUsl().doubleValue() - item.getLsl().doubleValue()) / (6 * sigma);
            double cpk = Math.min(item.getUsl().doubleValue() - mean, mean - item.getLsl().doubleValue()) / (3 * sigma);
            capability.put("cp", round(cp));
            capability.put("cpk", round(cpk));
        } else if (item.getUsl() != null) {
            capability.put("cp", null);
            capability.put("cpk", round((item.getUsl().doubleValue() - mean) / (3 * sigma)));
        } else {
            capability.put("cp", null);
            capability.put("cpk", round((mean - item.getLsl().doubleValue()) / (3 * sigma)));
        }
        return capability;
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).round(new MathContext(6, RoundingMode.HALF_UP)).doubleValue();
    }
}
