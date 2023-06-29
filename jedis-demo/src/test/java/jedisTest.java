
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Jedis;
import you.chanwill.util.JedisConnectionFactory;

public class jedisTest {
    private Jedis jedis;

    @BeforeEach
    void setUp(){
//        jedis = new Jedis("192.168.122.135",6379);
//        jedis.auth("123456");
        jedis = JedisConnectionFactory.getJedis();
        jedis.select(0);
    }

    @Test
    void test(){
        // 存入数据
        String result = jedis.set("name", "YOU");
        System.out.println(result);
        // 获取数据
        String s = jedis.get("you:user:2");
        System.out.println(s);
    }

    @AfterEach
    void tearDown(){
        if (jedis != null){
            jedis.close();
        }
    }
}
