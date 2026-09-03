package cn.bugstack.types.design.framework.link.model1;

public abstract class AbstractLogicLink<T, D, R> implements ILogicLink<T, D, R>{
    //持有对下一个处理节点的引用，形成单向链表结构。默认 null，表示当前节点是链尾。
    private ILogicLink<T, D, R> next;

    //获取后继节点。
    @Override
    public ILogicLink<T, D, R> next() {
        return next;
    }

    //将传入的节点 next 设置为当前节点的后继，并返回该后继节点。
    @Override
    public ILogicLink<T, D, R> appendNext(ILogicLink<T, D, R> next) {
        this.next = next;
        return next;
    }
    /** 这是一个委托方法，它将请求参数和动态上下文直接传递给下一个节点的 apply 方法，执行后续责任链的逻辑。
     * 典型用法是在当前节点的 apply 实现中调用 super.next(requestParameter, dynamicContext) 或 this.next(...) 来驱动整条链继续执行。
     * protected 修饰表示仅子类可以调用，避免外部直接驱动链。 */
    protected R next(T requestParameter, D dynamicContext) throws Exception{
        return next.apply(requestParameter, dynamicContext);
    }
}
