package se.unbound.tapestry.tagselect.components;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.tapestry5.SelectModel;
import org.apache.tapestry5.StreamResponse;
import org.apache.tapestry5.ValueEncoder;
import org.apache.tapestry5.dom.Document;
import org.apache.tapestry5.dom.Element;
import org.apache.tapestry5.internal.OptionModelImpl;
import org.apache.tapestry5.internal.SelectModelImpl;
import org.apache.tapestry5.test.PageTester;
import org.junit.Before;
import org.junit.Test;

import se.unbound.tapestry.tagselect.components.TagSelect.AutoCompleteCallback;
import se.unbound.tapestry.tagselect.helpers.Tag;
import se.unbound.tapestry.tagselect.mocks.ComponentResourcesMock;
import se.unbound.tapestry.tagselect.mocks.MarkupWriterFactoryMock;
import se.unbound.tapestry.tagselect.mocks.RequestMock;
import se.unbound.tapestry.tagselect.mocks.ResponseRendererMock;
import se.unbound.tapestry.tagselect.mocks.TypeCoercerMock;
import se.unbound.tapestry.tagselect.services.TagSource;
import se.unbound.tapestry.tagselect.services.TestModule;

public class TagSelectTest extends PageTester {
    private static final String PAGE_WITH_STRING_TAGS = "pagewithstringtags";
    private static final String PAGE_WITH_ENCODED_TAGS = "pagewithencodedtags";

    public TagSelectTest() {
        super("se.unbound.tapestry.tagselect", "TagSelect", "app", TestModule.class);
    }

    @Before
    public void setUp() {
        TagSource.TAGS.clear();
        TagSource.savedTags = null;
    }

    @Test
    public void componentRendersHiddenValuesField() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS);
        final Element element = document.getElementById("tags-values");
        assertEquals("element name", "input", element.getName());
        assertEquals("name", "tags-values", element.getAttribute("name"));
        assertEquals("type", "hidden", element.getAttribute("type"));
    }

    @Test
    public void componentRendersTextArea() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS);
        final Element element = document.getElementById("tags");
        assertEquals("element name", "textarea", element.getName());
        assertEquals("class", "u-textarea", element.getAttribute("class"));
        assertEquals("autocomplete", "off", element.getAttribute("autocomplete"));
    }

    @Test
    public void componentRendersAutocompleteMenu() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS);
        final Element element = document.getElementById("tags:menu");
        assertEquals("element name", "div", element.getName());
        assertEquals("class", "u-autocomplete-menu", element.getAttribute("class"));
    }

    @Test
    public void componentRendersTagContainer() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS);
        final Element container = document.getElementById("tags-tag-container");
        assertEquals("element name", "div", container.getName());
        assertEquals("class", "u-tag-container", container.getAttribute("class"));
        final Element ul = document.getElementById("tags-tags");
        assertEquals("element name", "ul", ul.getName());
        assertEquals("class", "u-tags", ul.getAttribute("class"));
    }

    @Test
    public void componentRendersExistingTag() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS + "/tag123");
        final Element element = document.getElementById("tags-tags");
        final Pattern pattern = Pattern
                .compile("<li id=\"u-tag-(\\d+)\" class=\"u-tag\">"
                        + "<button class=\"u-tag-button\" type=\"button\">"
                        + "<span><span class=\"u-tag-value\">tag123</span></span></button>"
                        + "<em onclick=\"TagSelect.removeSelection\\('tags', 'u-tag-\\1', 'tag123'\\)\" "
                        + "class=\"u-tag-remove\"></em></li>");
        final Matcher matcher = pattern.matcher(element.getChildMarkup());
        assertTrue(matcher.matches());
    }

    @Test
    public void componentSetsValuesFieldToSemiColonSeparatedStringOfClientValues() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS + "/tag123/tag456");
        final Element element = document.getElementById("tags-values");
        assertEquals("value", "tag123;tag456", element.getAttribute("value"));
    }

    @Test
    public void componentProcessesValueOfValuesFieldOnSubmit() {
        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_STRING_TAGS);
        final Element form = document.getElementById("form");
        final Map<String, String> params = new HashMap<String, String>();
        params.put("tags-values", "tag123;tag456");
        final Document document2 = this.submitForm(form, params);
        final Element element = document2.getElementById("tags-values");
        assertEquals("value", "tag123;tag456", element.getAttribute("value"));
    }

    @Test
    public void componentUsesEncoderDuringRenderIfSet() {
        TagSource.TAGS.add(new Tag(Long.valueOf(123), "Tag123"));
        TagSource.TAGS.add(new Tag(Long.valueOf(456), "Tag456"));

        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_ENCODED_TAGS);
        System.out.println(document.toString());
        final Element element = document.getElementById("tags-values");
        assertEquals("value", "123;456", element.getAttribute("value"));
    }

    @Test
    public void componentUsesEncoderDuringSubmitIfSet() {
        TagSource.TAGS.add(new Tag(Long.valueOf(123), "Tag123"));
        TagSource.TAGS.add(new Tag(Long.valueOf(456), "Tag456"));

        final Document document = this.renderPage(TagSelectTest.PAGE_WITH_ENCODED_TAGS);
        System.out.println(document.toString());
        final Element form = document.getElementById("form");
        final Map<String, String> params = new HashMap<String, String>();
        params.put("tags-values", "456");
        final Document document2 = this.submitForm(form, params);
        final Element element = document2.getElementById("tags-values");
        assertEquals("value", "456", element.getAttribute("value"));

        assertEquals("saved tags", 1, TagSource.savedTags.size());
        assertEquals("tag value", "Tag456", TagSource.savedTags.get(0).getValue());
    }

    @Test
    public void onAutocompleteReturnAStreamResponseWithTheGeneratedMarkup() throws Exception {
        final TagSelect tagSelect = new TagSelect();
        final RequestMock request = new RequestMock();
        request.addParameter("values", "");
        this.setPropertyValue(tagSelect, "request", request);
        this.setPropertyValue(tagSelect, "resources", new ComponentResourcesMock());
        this.setPropertyValue(tagSelect, "responseRenderer", new ResponseRendererMock());
        this.setPropertyValue(tagSelect, "factory", new MarkupWriterFactoryMock());
        this.setPropertyValue(tagSelect, "encoder", new ValueEncoderMock());

        final SelectModelImpl selectModel = new SelectModelImpl(new OptionModelImpl("label", "value"));
        this.setPropertyValue(tagSelect, "model", new AtomicReference<SelectModel>(selectModel));

        final StreamResponse streamResponse = (StreamResponse) tagSelect.onAutocomplete();
        assertNotNull("stream response", streamResponse);
        assertEquals("markup", "<html><ul><li id=\"client\">label</li></ul></html>",
                IOUtils.toString(streamResponse.getStream()));
    }

    @Test
    public void onAutocompleteIgnoresAlreadySelectedItemsInTheGeneratedMarkup() throws Exception {
        final TagSelect tagSelect = new TagSelect();
        final RequestMock request = new RequestMock();
        request.addParameter("values", "client");
        this.setPropertyValue(tagSelect, "request", request);
        this.setPropertyValue(tagSelect, "resources", new ComponentResourcesMock());
        this.setPropertyValue(tagSelect, "responseRenderer", new ResponseRendererMock());
        this.setPropertyValue(tagSelect, "factory", new MarkupWriterFactoryMock());
        this.setPropertyValue(tagSelect, "encoder", new ValueEncoderMock());

        final SelectModelImpl selectModel = new SelectModelImpl(new OptionModelImpl("label", "value"));
        this.setPropertyValue(tagSelect, "model", new AtomicReference<SelectModel>(selectModel));

        final StreamResponse streamResponse = (StreamResponse) tagSelect.onAutocomplete();
        assertNotNull("stream response", streamResponse);
        assertEquals("markup", "<html><ul></ul></html>", IOUtils.toString(streamResponse.getStream()));
    }

    @Test
    public void callbackStoresModelInAtomicReference() {
        final AtomicReference<SelectModel> atomicReference = new AtomicReference<SelectModel>();
        final AutoCompleteCallback callback = new TagSelect.AutoCompleteCallback(
                atomicReference, new TypeCoercerMock());

        final SelectModel model = new SelectModelImpl(new OptionModelImpl("label", "value"));
        callback.handleResult(model);

        assertNotNull("atomic reference", atomicReference.get());
        assertEquals("model", model, atomicReference.get());
    }

    private void setPropertyValue(final Object target, final String propertyName, final Object value) {
        final Field field = this.findField(target.getClass(), propertyName);

        this.assignValue(field, target, value);
    }

    private Field findField(final Class<? extends Object> targetClass, final String propertyName) {
        Field field = null;
        try {
            field = targetClass.getDeclaredField(propertyName);
        } catch (final SecurityException e) {
            throw new RuntimeException(e);
        } catch (final NoSuchFieldException e) {
            if (Object.class.getName().equals(targetClass.getName())) {
                throw new RuntimeException(e);
            }
            field = this.findField(targetClass.getSuperclass(), propertyName);
        }
        return field;
    }

    private void assignValue(final Field field, final Object target, final Object value) {
        try {
            field.setAccessible(true);
            field.set(target, value);
        } catch (final IllegalArgumentException e) {
            e.printStackTrace();
        } catch (final IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    private static class ValueEncoderMock implements ValueEncoder<String> {
        @Override
        public String toClient(final String value) {
            return "client";
        }

        @Override
        public String toValue(final String clientValue) {
            return "value";
        }
    }
}
