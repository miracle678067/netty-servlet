package com.github.netty.springboot;

import com.github.netty.core.util.AbstractRecycler;
import com.github.netty.core.util.LoggerFactoryX;
import com.github.netty.core.util.LoggerX;
import com.github.netty.core.util.ThreadPoolX;
import com.github.netty.rpc.RpcClientInstance;
import com.github.netty.rpc.RpcFuture;
import com.github.netty.servlet.ServletFilterChain;
import com.github.netty.servlet.handler.HttpMessageToServletRunnable;

import javax.servlet.Filter;
import java.math.BigDecimal;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 统计服务器信息的任务
 * @author 84215
 */
public class NettyReportRunnable implements Runnable{

    private LoggerX logger = LoggerFactoryX.getLogger(getClass());
    private AtomicInteger reportCount = new AtomicInteger();
    private long beginTime = System.currentTimeMillis();

    public static void start(){
        ThreadPoolX.getDefaultInstance().scheduleAtFixedRate(new NettyReportRunnable(),5,5, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        try {
            String timeoutApis = RpcClientInstance.getTimeoutApis();
            long spinResponseCount = RpcFuture.TOTAL_SPIN_RESPONSE_COUNT.get();
            long totalCount = RpcClientInstance.getTotalInvokeCount();
            long timeoutCount = RpcClientInstance.getTotalTimeoutCount();
            long successCount = totalCount - timeoutCount;
            double rate = totalCount == 0? 0:(double) successCount/(double) totalCount * 100;
            double rateSpinResponseCount = totalCount==0?0:(double) spinResponseCount/(double) totalCount * 100;

            long totalTime = System.currentTimeMillis() - beginTime;

            long servletQueryCount = HttpMessageToServletRunnable.SERVLET_QUERY_COUNT.get();
            long servletAndFilterTime = HttpMessageToServletRunnable.SERVLET_AND_FILTER_TIME.get();
            long servletTime = ServletFilterChain.SERVLET_TIME.get();
            long filterTime = ServletFilterChain.FILTER_TIME.get();

            double servletAndFilterAvgRuntime = servletQueryCount == 0? 0:(double)servletAndFilterTime/(double)servletQueryCount;
            double servletAvgRuntime = servletQueryCount ==0? 0:(double)servletTime/(double)servletQueryCount;
            double filterAvgRuntime = servletQueryCount ==0? 0:(double)filterTime/(double)servletQueryCount;

            StringJoiner filterJoin = new StringJoiner(", ");
            for(Filter filter : ServletFilterChain.FILTER_SET){
//                    double filterAvgTime = (double)e.getValue().get() / (double)servletQueryCount;
                filterJoin.add(
                        filter.getClass().getSimpleName()
                );
            }

            StringJoiner joiner = new StringJoiner(", ");
            joiner.add("\r\n第"+reportCount.incrementAndGet()+"次统计 ");
            joiner.add("时间="+(totalTime/60000)+"分"+((totalTime % 60000 ) / 1000)+"秒 ");
            joiner.add("rpc调用次数=" + successCount);
            joiner.add("超时次数=" + timeoutCount);
            joiner.add("自旋成功数=" + spinResponseCount);
            joiner.add("自旋成功率=" + formatRate(rateSpinResponseCount,2)+ "%, ");
            joiner.add("调用成功率=" + formatRate(rate,2)+"%, ");
            joiner.add("超时api="+ timeoutApis);
            joiner.add("servlet执行次数="+ servletQueryCount);
            joiner.add("servlet+filter平均时间="+ formatRate(servletAndFilterAvgRuntime,4)+"ms,");
            joiner.add("servlet平均时间="+ formatRate(servletAvgRuntime,4)+"ms, ");
            joiner.add("filter平均时间="+ formatRate(filterAvgRuntime,4)+"ms, ");
//            joiner.add("\r\n "+filterJoin.toString());

            int recyclerTotal = AbstractRecycler.TOTAL_COUNT.get();
            int recyclerHit = AbstractRecycler.HIT_COUNT.get();
            double hitRate = (double) recyclerHit/(double) recyclerTotal;
            joiner.add("\r\n获取实例次数="+ recyclerTotal+"次");
            joiner.add("实例命中="+ recyclerHit+"次");
            joiner.add("实例命中率="+ formatRate(hitRate * 100,0)+"%");

            addMessage(joiner);

            logger.info(joiner.toString());
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private String formatRate(double num, int rate){
        if(Double.isNaN(num)){
            return "0";
        }
        if(num == (int)num){
            return String.valueOf(((int)num));
        }
        return new BigDecimal(num).setScale(rate,BigDecimal.ROUND_HALF_DOWN).stripTrailingZeros().toString();
    }

    protected void addMessage(StringJoiner messageJoiner){

    }

}