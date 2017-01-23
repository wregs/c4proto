package ee.cone.c4gate

import java.util.UUID

import ee.cone.c4actor.LEvent.{add, delete, update}
import ee.cone.c4actor.Types.SrcId
import ee.cone.c4actor._
import ee.cone.c4assemble.Types.{Values, World}
import ee.cone.c4assemble.{Assemble, assemble, by}
import ee.cone.c4gate.BranchTypes.LocationHash
import ee.cone.c4gate.TestTodoProtocol.TodoTask
import ee.cone.c4proto.{Id, Protocol, protocol}
import ee.cone.c4vdom.{ChildPair, RootView, Tags, VDomLens}
import ee.cone.c4vdom_impl.UntilElement

object TestTodo extends Main((new TestTodoApp).execution.run)

class TestTodoApp extends ServerApp
  with EnvConfigApp
  with KafkaProducerApp with KafkaConsumerApp
  with SerialObserversApp
  with VDomSSEApp
{

  //  println(s"visit http://localhost:${config.get("C4HTTP_PORT")}/react-app.html")

  lazy val testTags = new TestTags[World](childPairFactory,tagJsonUtils)
  override def protocols: List[Protocol] = TestTodoProtocol :: super.protocols
  override def assembles: List[Assemble] = new TestTodoAssemble :: super.assembles
}

@protocol object TestTodoProtocol extends Protocol {
  @Id(0x0001) case class TodoTask(
    @Id(0x0002) srcId: String,
    @Id(0x0003) createdAt: Long,
    @Id(0x0004) comments: String
  )
}

@assemble class TestTodoAssemble extends Assemble {
  type All = SrcId
  def joinTodoTasks(
    key: SrcId,
    todoTasks: Values[TodoTask]
  ): Values[(All,TodoTask)] =
    for(todoTask ← todoTasks) yield "" → todoTask


  def joinView(
    key: SrcId,
    @by[LocationHash] branchTasks: Values[BranchTask],
    @by[All] todoTasks: Values[TodoTask]
  ): Values[(SrcId,View)] =
    for(branchTask ← branchTasks if key == "")
      yield branchTask.branchKey → TestTodoRootView(branchTask,todoTasks.sortBy(-_.createdAt))

}

case class TestTodoRootView(branchTask: BranchTask, todoTasks: Values[TodoTask])/*(tags: TestTags[World])*/ extends View {
  def view: World ⇒ List[ChildPair[_]] = local ⇒ {
    val startTime = System.currentTimeMillis
    val tags: TestTags[World] = ??? //TxKey.of(local).world
    val mTags: Tags = ???
    val taskLines = todoTasks.map { task =>
      tags.div(
        task.srcId,
        List(
          tags.input("comments", task.comments,
            value ⇒ add(update(task.copy(comments=value)))
            //value ⇒ (task:Task) ⇒ task.copy(comments=value)
          ),
          tags.button("remove", "-", add(delete(task)))
        )
      )
    }
    val btnList = List(
      tags.button("add", "+",
        add(update(TodoTask(UUID.randomUUID.toString,System.currentTimeMillis,"")))
      )
    )
    val res = List(btnList,taskLines).flatten
    val endTime = System.currentTimeMillis
    val until = endTime+(endTime-startTime)*10
    mTags.until("until",until) :: res
  }
}

