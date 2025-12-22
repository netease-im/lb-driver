package com.netease.nim.lbd.test;

import com.netease.nim.lbd.util.AdjustCount;
import com.netease.nim.lbd.util.AutoAdjustQueue;
import org.junit.Test;

/**
 * Created by caojiajun on 2025/12/4
 */
public class AutoAdjustQueueTest {

    @Test
    public void test() {
        AutoAdjustQueue<String> queue = new AutoAdjustQueue<>();;
        AdjustCount<String> count1 = queue.createCountElement("a1");
        AdjustCount<String> count2 = queue.createCountElement("a2");
        AdjustCount<String> count3 = queue.createCountElement("a3");
        AdjustCount<String> count4 = queue.createCountElement("a4");

        count1.increaseAndGet();
        count1.increaseAndGet();
        count2.increaseAndGet();
        count3.increaseAndGet();
        count3.increaseAndGet();
        count3.increaseAndGet();
        count4.increaseAndGet();


        System.out.println(queue.peekHead());
        count4.increaseAndGet();//2
        System.out.println(queue.peekHead());
        count2.increaseAndGet();//2
        System.out.println(queue.peekHead());
        count4.decreaseAndGet();
        count4.decreaseAndGet();
        count4.decreaseAndGet();//0
        System.out.println(queue.peekHead());
    }
}
