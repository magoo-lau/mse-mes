package vip.mseapp.mes.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/**
 * 批量逐条执行器：对勾选的 id 列表逐条调用单条操作（方法引用指向代理 Bean，行级事务照常生效），
 * 部分成功语义——失败项跳过并收集「id=xxx：原因」，不回滚已成功项。
 */
@Service
public class MesBatchRunner {

    public BatchOpResult run(List<Long> ids, LongConsumer action) {
        List<String> errors = new ArrayList<>();
        int success = 0;
        for (Long id : ids) {
            try {
                action.accept(id);
                success++;
            } catch (Exception e) {
                errors.add("id=" + id + "：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }
        return new BatchOpResult(success, ids.size() - success, errors);
    }
}
