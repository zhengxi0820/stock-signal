package com.stocksignal.data.mapper;

import com.stocksignal.data.entity.JobRun;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * job_run 表访问接口（运维表）。
 */
public interface JobRunMapper {

    /** 阶段开始：插入或重置该阶段记录为 RUNNING。 */
    int upsertRunning(JobRun jobRun);

    /** 阶段结束：更新状态/统计/错误。 */
    int finish(JobRun jobRun);

    /** 查询某市场最近一次指定阶段成功的记录（数据新鲜度）。 */
    JobRun findLatestSuccess(@Param("market") String market, @Param("phase") String phase);

    /** 每个市场最近一次运行的 run_id（用于运行状态展示）。 */
    List<JobRun> findLatestRunPerMarket();

    /** 某市场最近一次运行（任一阶段）的开始时间；无记录返回 null。用于触发冷却判断。 */
    java.time.LocalDateTime findLatestStartByMarket(@Param("market") String market);

    /** 某市场近 2 小时内仍处于 RUNNING 的阶段数（防并发运行；超 2 小时视为僵死不计）。 */
    int countRunningByMarket(@Param("market") String market);

    /** 启动时清理：把残留 RUNNING 阶段标记为 FAILED（单实例应用，启动时不存在合法进行中运行）。返回清理行数。 */
    int failStaleRunning(@Param("error") String error);

    /** 某次运行的全部阶段记录。 */
    List<JobRun> findByRunId(@Param("runId") String runId);
}
