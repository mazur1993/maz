import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import java.util.*;



public class Checkpoint extends UntypedActor {
//////////////////////////////////////////////////////////
    private int num_at_a_run=0;
    private Gender current_gender = Gender.MAN;
    private int num_in_shower=0;

    enum Gender{MAN,WOMAN}
    private final int NUM_OF_SHOWER_STALL  = 4;
    private final int MAX_NUM_AT_A_RUN  = 4;
//////////////////////////////////////////////////////////
    static class WantShower {
        private Gender gender;
        private Integer number;
        public WantShower(Gender gender,Integer number)
        {
            this.gender = gender;
            this.number = number;
        }

        @Override
        public String toString() {
            return "WantShower{" +
                    "gender=" + gender +
                    ", number=" + number +
                    '}';
        }
    }

    static class BackFromShower {
        private final Integer key;
        private final Gender gender;
        public BackFromShower(Integer key, Gender gender) {
            this.key = key;
            this.gender = gender;
        }

        @Override
        public String toString() {
            return "BackFromShower{" +
                    "key=" + key +
                    ", gender=" + gender +
                    '}';
        }
    }


    private Queue<WantShower> mansQueue = new ArrayDeque<>();
    private Queue<WantShower> noMansQueue = new ArrayDeque<>();


    private LoggingAdapter log = Logging.getLogger(getContext().system(), this);

    //LinkedHashMap<Integer, Gender> k_g = new LinkedHashMap<Integer, Gender>();

    private Map<Integer, ActorRef> requests = new HashMap<>();

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof WantShower) {
            processWantShower((WantShower) message);
        }
        else if (message instanceof BackFromShower) {
            processBackFromShower((BackFromShower) message);
        }
    }

    private void goToShower(Integer key, Gender gender) {
        ActorRef shower = getContext().actorOf(Props.create(ShowerStall.class));
        shower.tell(new ShowerStall.Washing(key,gender), getSelf());
    }

    private void processBackFromShower(BackFromShower message) {
        log.info("Processed {}", message);
        // парень помылся и выходит
        ActorRef realRequestSender = requests.get(message.key);
        // если парня нет, пропускаем
        if (realRequestSender == null)
            return;

        num_in_shower--;
        realRequestSender.tell(message, getSelf());
        // достаём из очереди ожидающих
        Queue<WantShower> currentQueue = getCurrentQueue();
        if (currentQueue.isEmpty()) {
            switchGender();
            currentQueue = getCurrentQueue();
        }
        processQueue(currentQueue);
    }

    private Queue<WantShower> getCurrentQueue() {
        if (Gender.MAN.equals(current_gender))
            return mansQueue;

        return noMansQueue;
    }

    private void processQueue(Queue<WantShower> queue) {
        log.info("Processing queue {}, \n num_at_run = {}, \n num_in_shower = {}", queue, num_at_a_run, num_in_shower);
        while (!queue.isEmpty() && num_at_a_run < MAX_NUM_AT_A_RUN && num_in_shower < NUM_OF_SHOWER_STALL) {
            WantShower wantShower = queue.poll();
            processWantShower(wantShower);
        }
    }

    private void processMaxAtRun() {
        if (num_at_a_run == MAX_NUM_AT_A_RUN)
            switchGender();
    }

    private void switchGender() {
        Gender prev = current_gender;
        int prevNumAtRun = num_at_a_run;
        if (Gender.MAN.equals(current_gender))
            current_gender = Gender.WOMAN;
        else
            current_gender = Gender.MAN;
        num_at_a_run = 0;
        log.info("Gender switched from {} to {}, num_at_run was = {}", prev, current_gender, prevNumAtRun);
    }

    private void processWantShower(WantShower message) {
        //запоминаем номер запроса и кому отдать сообщение 
        log.info("Received {}, num_at_run = {}, num_in_shower = {}", message, num_at_a_run, num_in_shower);
        requests.put(message.number, getSender());

        boolean wasWashed = false;
        // если есть пустые кабинки
        if (num_in_shower < NUM_OF_SHOWER_STALL) {
            // и можно отправить в душe
            if (current_gender.equals(message.gender)) {
                // то отправляем
                goToShower(message.number, message.gender);
                wasWashed = true;
                // обновляем счётчики
                updateCounters();
            }
        }
        // если не отправили
        if (!wasWashed) {
            // добавляем в очередь
            log.info("Request {} was queued", message);
            if (Gender.MAN.equals(message.gender))
                mansQueue.add(message);
            else
                noMansQueue.add(message);
        }
    }

    private void updateCounters() {
        num_in_shower++;
        num_at_a_run++;
        // меняем пол, если нужно
        processMaxAtRun();
    }
}
