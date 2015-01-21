/**
 * 
 */
package org.jocean.xharbor.route;

import java.net.URI;

import org.jocean.idiom.Function;
import org.jocean.idiom.InterfaceUtils;
import org.jocean.idiom.SimpleCache;
import org.jocean.idiom.Tuple;
import org.jocean.idiom.Visitor2;
import org.jocean.j2se.MBeanRegisterSupport;
import org.jocean.xharbor.relay.RelayContext;
import org.jocean.xharbor.relay.RelayContext.RESULT;
import org.jocean.xharbor.relay.RelayContext.RelayMemo;
import org.jocean.xharbor.relay.RelayContext.STEP;
import org.jocean.xharbor.spi.Router;
import org.jocean.xharbor.util.BizMemoImpl;
import org.jocean.xharbor.util.TIMemoImplOfRanges;
import org.jocean.xharbor.util.TimeIntervalMemo;

import com.google.common.collect.Range;

/**
 * @author isdom
 */
public class URI2RelayCtxOfRoutingInfo implements Router<URI, RelayContext> {

    public URI2RelayCtxOfRoutingInfo() {
        _mbeanSupport.registerMBean("name=relays", this._level0Memo.createMBean());
    }
    
    private static final String[] _OBJNAME_KEYS = new String[]{"path", "method", "dest"};

    private static final String normalizeString(final String input) {
        return input.replaceAll(":", "-");
    }
    
    private static final String uri2value(final URI uri) {
        return normalizeString(uri.toString());
    }
    
    @Override
    public RelayContext calculateRoute(final URI uri, final Context routectx) {
        final RoutingInfo info = routectx.getProperty("routingInfo");
        final TargetSet targetSet = routectx.getProperty("targetSet");
        final RelayContext.RelayMemo memoBase = 
                InterfaceUtils.combineImpls(RelayContext.RelayMemo.class, 
                this._level0Memo,
                this._bizMemos.get(Tuple.of(normalizeString(info.getPath()))),
                this._bizMemos.get(Tuple.of(normalizeString(info.getPath()), info.getMethod()))
                );
        final RelayContext.RelayMemo memo = 
                null != uri 
                ? InterfaceUtils.combineImpls(RelayContext.RelayMemo.class, 
                    memoBase,
                    this._bizMemos.get(Tuple.of(normalizeString(info.getPath()), info.getMethod(), uri2value(uri))),
                    new RelayContext.RelayMemo() {
                        @Override
                        public void beginBizStep(final STEP step) {
                        }
                        @Override
                        public void endBizStep(final STEP step, final long ttl) {
                            if ( step.equals(STEP.RECV_RESP) ) {
                                //  < 500 ms
                                if ( ttl < 500 ) {
                                    targetSet.updateWeight(uri, 1);
                                }
                            }
                        }
                        @Override
                        public void incBizResult(final RESULT result, final long ttl) {
                            if ( result.equals(RESULT.CONNECTDESTINATION_FAILURE)) {
                                targetSet.markTargetDown(uri);
                            }
                        }})
                : memoBase;
        
        return new RelayContext() {

            @Override
            public URI relayTo() {
                return uri;
            }

            @Override
            public RelayMemo memo() {
                return memo;
            }};
    }

//    private enum Range_10ms_30s implements RangeSource<Long> {
//        range_1_lt10ms(Range.closedOpen(0L, 10L)),
//        range_2_lt100ms(Range.closedOpen(10L, 100L)),
//        range_3_lt500ms(Range.closedOpen(100L, 500L)),
//        range_4_lt1s(Range.closedOpen(500L, 1000L)),
//        range_5_lt5s(Range.closedOpen(1000L, 5000L)),
//        range_6_lt10s(Range.closedOpen(5000L, 10000L)),
//        range_7_lt30s(Range.closedOpen(10000L, 30000L)),
//        range_8_mt30s(Range.atLeast(30000L)),
//        ;
//
//        Range_10ms_30s(final Range<Long> range) {
//            this._range = range;
//        }
//        
//        @Override
//        public Range<Long> range() {
//            return _range;
//        }
//        
//        private final Range<Long> _range;
//    }
    
//    private static class RelayTIMemoImpl extends TIMemoImpl<Range_10ms_30s> {
//        
//        public RelayTIMemoImpl() {
//            super(Range_10ms_30s.class);
//        }
//    }
    
      private static class RelayTIMemoImpl extends TIMemoImplOfRanges {
      
          @SuppressWarnings("unchecked")
        public RelayTIMemoImpl() {
              super(new String[]{
                      "lt10ms",
                      "lt100ms",
                      "lt500ms",
                      "lt1s",
                      "lt5s",
                      "lt10s",
                      "lt30s",
                      "mt30s",
                      },
                      new Range[]{
                      Range.closedOpen(0L, 10L),
                      Range.closedOpen(10L, 100L),
                      Range.closedOpen(100L, 500L),
                      Range.closedOpen(500L, 1000L),
                      Range.closedOpen(1000L, 5000L),
                      Range.closedOpen(5000L, 10000L),
                      Range.closedOpen(10000L, 30000L),
                      Range.atLeast(30000L)});
          }
    }
    
    private static class RelayMemoImpl extends BizMemoImpl<RelayMemoImpl, STEP, RESULT> 
        implements RelayMemo {
        
        public RelayMemoImpl() {
            super(STEP.class, RESULT.class);
        }
    }
    
    private final MBeanRegisterSupport _mbeanSupport = 
            new MBeanRegisterSupport("org.jocean:type=router", null);
    
    private Function<Tuple, RelayTIMemoImpl> _ttlMemoMaker = new Function<Tuple, RelayTIMemoImpl>() {
        @Override
        public RelayTIMemoImpl apply(final Tuple tuple) {
            return new RelayTIMemoImpl();
        }};
        
    private Visitor2<Tuple, RelayTIMemoImpl> _ttlMemoRegister = new Visitor2<Tuple, RelayTIMemoImpl>() {
        @Override
        public void visit(final Tuple tuple, final RelayTIMemoImpl newMemo) throws Exception {
            final StringBuilder sb = new StringBuilder();
            Character splitter = null;
            //                      for last Enum<?>
            for ( int idx = 0; idx < tuple.size()-1; idx++) {
                if (null != splitter) {
                    sb.append(splitter);
                }
                sb.append(_OBJNAME_KEYS[idx]);
                sb.append("=");
                sb.append((String)tuple.getAt(idx));
                splitter = ',';
            }
            final Enum<?> stepOrResult = tuple.getAt(tuple.size()-1);
            final String category = stepOrResult.getClass().getSimpleName();
            final String ttl = stepOrResult.name();
            if (null != splitter) {
                sb.append(splitter);
            }
            sb.append("category=");
            sb.append(category);
            sb.append(',');
            sb.append("ttl=");
            sb.append(ttl);
            
            _mbeanSupport.registerMBean(sb.toString(), newMemo.createMBean());
        }};
            
    private SimpleCache<Tuple, RelayTIMemoImpl> _ttlMemos  = 
            new SimpleCache<Tuple, RelayTIMemoImpl>(this._ttlMemoMaker, this._ttlMemoRegister);
    
    private final Function<Tuple, RelayMemoImpl> _bizMemoMaker = 
            new Function<Tuple, RelayMemoImpl>() {
        @Override
        public RelayMemoImpl apply(final Tuple tuple) {
            return new RelayMemoImpl()
                .fillTimeIntervalMemoWith(new Function<Enum<?>, TimeIntervalMemo>() {
                    @Override
                    public TimeIntervalMemo apply(final Enum<?> e) {
                        return _ttlMemos.get(tuple.append(e));
                    }});
        }};

    private final Visitor2<Tuple, RelayMemoImpl> _bizMemoRegister = 
            new Visitor2<Tuple, RelayMemoImpl>() {
        @Override
        public void visit(final Tuple tuple, final RelayMemoImpl newMemo)
                throws Exception {
            final StringBuilder sb = new StringBuilder();
            Character splitter = null;
            for ( int idx = 0; idx < tuple.size(); idx++) {
                if (null != splitter) {
                    sb.append(splitter);
                }
                sb.append(_OBJNAME_KEYS[idx]);
                sb.append("=");
                sb.append((String)tuple.getAt(idx));
                splitter = ',';
            }
            _mbeanSupport.registerMBean(sb.toString(), newMemo.createMBean());
        }};
        
    private final SimpleCache<Tuple, RelayMemoImpl> _bizMemos = 
            new SimpleCache<Tuple, RelayMemoImpl>(this._bizMemoMaker, this._bizMemoRegister);
    
    private RelayMemoImpl _level0Memo = new RelayMemoImpl()
        .fillTimeIntervalMemoWith(new Function<Enum<?>, TimeIntervalMemo>() {
            @Override
            public TimeIntervalMemo apply(final Enum<?> e) {
                return _ttlMemos.get(Tuple.of(e));
            }});
}
