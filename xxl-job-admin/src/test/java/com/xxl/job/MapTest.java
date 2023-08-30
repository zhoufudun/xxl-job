package com.xxl.job;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 今天抽空整理一下LinkedHashMap的插入顺序和访问顺序;
 * 之前写过对应的一个Demo,但是没有详尽的注释与说明;
 * 后续自己阅读也不是很方便,于是今天抽空来重新编辑一下;
 *
 * 那么什么是LinkedHashMap的插入顺序和访问顺序呢?
 * 插入顺序:是指LinkedHashMap在数据插入时的插入顺序;
 * 	比如说1,2,3,4...数据依次从小到大插入;
 * 	若按照插入顺序输出,输出结果就是1,2,3,4...
 * 访问顺序:则是说同样按照插入1,2,3,4...从小到大有序的插入;
 * 	如果在插入后你随机访问了某个元素,那么那个元素则会排列到集合的最后一位;
 *
 * 为了更清晰的认识LinkedHashMap的插入顺序和访问顺序,以下随手写了一个
 * 小Demo,供大家参考:
 *
 * https://blog.csdn.net/dalei9243/article/details/106108840
 */
public class MapTest {

    @Test
    public void orderTest() throws Exception {

        /**
         * 在这里我们调用封装好的数据接口;
         * 接口中会提前把插入好的数据打印一次;
         * 然后我们在这里开始调用打印测试;
         */
        Map<String, String> orderMap = orderMap();

        // 首先调用数据中的3元素;
        orderMap.get("key_3");
        orderMap.get("key_2");
        orderMap.get("key_1");

        /**
         * 然后以同样的方式遍历打印Entry对象;
         * 注意:此时是程序开始的第二次数据打印;
         */
        Set<Map.Entry<String,String>> entrySet = orderMap.entrySet();
        for(Map.Entry<String,String> entry : entrySet){
            System.out.println("key:"+entry.getKey()+";  Value: "+entry.getValue());
        }

        System.out.println("最老的数据为："+orderMap.entrySet().iterator().next().getKey());
    }

    public static Map<String,String> orderMap(){

        /**
         * 实例化一个LinkedHashMap;
         *
         * LinkedHashMap的插入顺序和访问顺序;
         * LinkedHashMap(int initialCapacity, float loadFactor, boolean accessOrder);
         * 说明:
         * 	当accessOrder为true时表示当前数据的插入读取顺序为访问顺序；
         * 	当accessOrder为false时表示当前数据的插入读取顺序为插入顺序；
         */
        Map<String,String> linkedHashMap = new LinkedHashMap<String,String>(0,1.6f,true); // 访问顺序;
//		Map<String,String> linkedHashMap = new LinkedHashMap<String,String>(0,1.6f,false); // 插入顺序;

        // 数据插入;
        linkedHashMap.put("key_1", "value_11111");
        linkedHashMap.put("key_2", "value_22222");
        linkedHashMap.put("key_3", "value_33333");
        linkedHashMap.put("key_4", "value_44444");
        linkedHashMap.put("key_5", "value_55555");

        /**
         * 打印集合数据,看输出顺序是什么样子?
         * 首先获取Map对象的Entry对象集;
         * 然后遍历打印Entry对象;
         * 注意:此时是程序开始的第一次数据打印;
         */
        Set<Map.Entry<String,String>> entrySet = linkedHashMap.entrySet();
        for(Map.Entry<String,String> entry : entrySet){
            System.out.println("key:"+entry.getKey()+";  Value: "+entry.getValue());
        }

        // 在这里做一条两次打印的分界线;
        System.out.println("--------------------------------------------");

        // 最终我们把集合返回;
        return linkedHashMap;
    }
}
